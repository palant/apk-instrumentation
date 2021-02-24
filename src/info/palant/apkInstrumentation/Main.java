/*
 * This Source Code is subject to the terms of the Mozilla Public License
 * version 2.0 (the "License"). You can obtain a copy of the License at
 * http://mozilla.org/MPL/2.0/.
 */

package info.palant.apkInstrumentation;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import soot.G;
import soot.PackManager;
import soot.Transform;
import soot.options.Options;

public class Main
{
  final static String INPUT_FILE = "input.dex";
  final static String OUTPUT_FILE = "classes.dex";

  public static void main(String[] args) throws IOException
  {
    String configPath = (args.length >= 1 ? args[0] : "config.properties");
    Properties config = readConfig(configPath);
    if (config == null)
    {
      System.err.println("Configuration file " + configPath + " does not exist or is not a regular file.");
      System.exit(1);
    }

    String sdkDir = config.getProperty("sdk");
    if (sdkDir == null)
    {
      if(!System.getenv().containsKey("ANDROID_HOME"))
      {
        System.err.println("Please either add sdk option to config file or set ANDROID_HOME environment variable.");
        System.exit(2);
      }
      sdkDir = System.getenv("ANDROID_HOME");
    }

    String input = config.getProperty("input");
    if (input == null)
    {
      System.err.println("Please add input option to config file.");
      System.exit(3);
    }

    String output = config.getProperty("output");
    if (output == null)
    {
      System.err.println("Please add output option to config file.");
      System.exit(4);
    }

    String keystore = config.getProperty("keystore");
    String keypass = config.getProperty("keypass");
    if (keystore == null || keypass == null)
      System.err.println("Warning: keystore or keypass missing in the config file, package will not be signed.");

    String platformsPath = sdkDir + File.separator + "platforms";
    File tempDir = Files.createTempDirectory(null).toFile();
    try
    {
      transformAPK(config, input, output, tempDir.getPath(), platformsPath);
    }
    finally
    {
      for (String entry: tempDir.list())
        new File(tempDir, entry).delete();
      tempDir.delete();
    }

    if (keystore != null && keypass != null)
      signAPK(sdkDir, keystore, keypass, output);
  }

  private static Properties readConfig(String configPath) throws IOException
  {
    Properties result = new Properties();
    try
    {
      FileInputStream inputStream = new FileInputStream(configPath);
      result.load(inputStream);
      inputStream.close();
      return result;
    }
    catch (FileNotFoundException e)
    {
      return null;
    }
  }

  private static void setupSoot(String platformsPath, String tempDir, String inputFile)
  {
    // Reset all Soot settings
    G.reset();

    // Generic options
    Options.v().set_allow_phantom_refs(true);
    Options.v().set_prepend_classpath(true);

    // Read (APK Dex-to-Jimple) Options
    Options.v().set_android_jars(platformsPath);
    Options.v().set_src_prec(Options.src_prec_apk);
    Options.v().set_process_dir(Collections.singletonList(inputFile));
    Options.v().set_include_all(true);

    // Write (APK Generation) Options
    Options.v().set_output_format(Options.output_format_dex);
    Options.v().set_output_dir(tempDir);
    Options.v().set_validate(true);
  }

  private static void addTransformers(Properties config)
  {
    boolean hasTransformers = false;
    if (config.getProperty("MethodLogger.enabled") != null)
    {
      PackManager.v().getPack("jtp").add(new Transform("jtp.MethodLogger", new MethodLogger(config)));
      hasTransformers = true;
    }
    if (config.getProperty("AssignmentRemover.enabled") != null)
    {
      PackManager.v().getPack("jtp").add(new Transform("jtp.AssignmentRemover", new AssignmentRemover(config)));
      hasTransformers = true;
    }
    if (config.getProperty("DownloadLogger.enabled") != null)
    {
      PackManager.v().getPack("jtp").add(new Transform("jtp.DownloadLogger", new DownloadLogger(config)));
      hasTransformers = true;
    }

    if (!hasTransformers)
    {
      System.err.println("No transformers are enabled in the config.");
      System.exit(5);
    }
  }

  private static void transformAPK(Properties config, String input, String output, String tempDir, String platformsPath) throws IOException
  {
    String dexInput = tempDir + File.separator + INPUT_FILE;
    String dexOutput = tempDir + File.separator + OUTPUT_FILE;

    ZipInputStream zipInput = new ZipInputStream(new FileInputStream(input));
    ZipOutputStream zipOutput = new ZipOutputStream(new FileOutputStream(output));
    ZipEntry entry;
    byte[] buffer = new byte[1024*64];
    while ((entry = zipInput.getNextEntry()) != null)
    {
      ZipEntry newEntry = new ZipEntry(entry);
      newEntry.setCompressedSize(-1);
      zipOutput.putNextEntry(newEntry);
      if (entry.getName().endsWith(".dex"))
      {
        FileOutputStream dexOutputStream = new FileOutputStream(dexInput);
        int numBytes;
        while ((numBytes = zipInput.read(buffer, 0, buffer.length)) != -1)
          dexOutputStream.write(buffer, 0, numBytes);
        dexOutputStream.close();

        setupSoot(platformsPath, tempDir, dexInput);
        addTransformers(config);
        PackManager.v().runPacks();
        PackManager.v().writeOutput();

        FileInputStream dexInputStream = new FileInputStream(dexOutput);
        while ((numBytes = dexInputStream.read(buffer)) != -1)
          zipOutput.write(buffer, 0, numBytes);
        dexInputStream.close();
      }
      else
      {
        int numBytes;
        while ((numBytes = zipInput.read(buffer, 0, buffer.length)) != -1)
          zipOutput.write(buffer, 0, numBytes);
      }
      zipInput.closeEntry();
      zipOutput.closeEntry();
    }
    zipInput.close();
    zipOutput.close();
  }

  private static void runCommand(String[] command) throws IOException
  {
    ProcessBuilder builder = new ProcessBuilder(command);
    builder.inheritIO();
    Process process = builder.start();
    try
    {
      int result = process.waitFor();
      if (result != 0)
        throw new RuntimeException(command[0] + " exited with error code " + result);
    }
    catch (InterruptedException e)
    {
      throw new RuntimeException(command[0] + " interrupted", e);
    }
  }

  private static void signAPK(String sdkDir, String keystore, String keypass, String output) throws IOException
  {
    File buildToolsDir = null;
    for (File file: new File(sdkDir, "build-tools").listFiles())
    {
      if (!file.isDirectory())
        continue;
      if (buildToolsDir == null || file.getName().compareTo(buildToolsDir.getName()) > 0)
        buildToolsDir = file;
    }

    if (buildToolsDir == null)
      throw new RuntimeException("Could not find build tools in Android SDK directory " + sdkDir);

    String suffix = "";
    if (System.getProperty("os.name").toLowerCase().startsWith("win"))
      suffix = ".exe";

    File zipalign = new File(buildToolsDir, "zipalign" + suffix);
    File tempDir = Files.createTempDirectory(null).toFile();
    try
    {
      File tempFile =  new File(tempDir, "aligned.apk");
      runCommand(new String[] {
        zipalign.getPath(),
        "-f", "4",
        output,
        tempFile.getPath()
      });
      Files.copy(tempFile.toPath(), new File(output).toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
    finally
    {
      for (String entry: tempDir.list())
        new File(tempDir, entry).delete();
      tempDir.delete();
    }

    File apksigner = new File(buildToolsDir, "apksigner" + suffix);
    runCommand(new String[] {
      apksigner.getPath(),
      "sign",
      "--ks", keystore,
      "--ks-pass", "pass:" + keypass,
      output
    });
  }
}
