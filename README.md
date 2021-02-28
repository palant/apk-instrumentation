# APK Instrumentation

**Warning**: This is a repository with quick-and-dirty code rewriting tools based on [Soot](https://soot-oss.github.io/soot/) which I use for my own research. The documentation is limited and support cannot be provided. Use at your own risk!

## Getting started

You can download `apk-instrumentation.jar` from the [Releases section](https://github.com/palant/apk-instrumentation/releases) of this repository. It is self-contained and requires nothing but Java. If you want to build it yourself, you will need Python 3 and Soot (JAR file with all dependencies). You then run the build command:

    ./build /path/to/soot-jar-with-dependencies.jar

To start the APK conversion process, use the following command:

    java -jar apk-instrumentation.jar [/path/to/config.properties]

If no path to `config.properties` is given on the command line, the file is assumed to be present in the current directory. Its entries determine what code transformations should be performed.

## General configuration options

The following configuration options are independent of the components enabled:

* `sdk`: (optional) directory where the Android SDK is installed. If omitted, `ANDROID_HOME` environment variable has to be set.
* `input`: path to the input APK file
* `output`: path of the instrumented APK file to be written
* `keystore`: (optional) path to the key store containing the signing key
* `keypass`: (optional) password protecting the key store

## Filters

Each component has a `filter` option allowing to restrict its functionality. It’s a space-separated list, entries can have the following format:

* `com.example.test.*`: includes all classes with names matching a particular prefix
* `com.example.test.Main`: includes all methods of a specific class
* `com.example.test.Main.dump()`: includes all methods with a particular name inside a class (empty parentheses at the end are mandatory)

## MethodLogger component

This component will add logging to the start of each method. In addition to the method signature, the parameter values will be logged.

Configuration options:

* `MethodLogger.enabled`: add to enable this component
* `MethodLogger.filter`: (optional) restricts functionality to a set of classes or methods (see Filters section above)
* `MethodLogger.tag`: (optional) log tag to be used (default is `MethodLogger`)

## DownloadLogger component

This component will log `URLConnection` interactions.

Configuration options:

* `DownloadLogger.enabled`: add to enable this component
* `DownloadLogger.filter`: (optional) restricts functionality to a set of classes or methods (see Filters section above)
* `DownloadLogger.tag`: (optional) log tag to be used (default is `DownloadLogger`)
* `DownloadLogger.requestBodies`: (optional) if present, data sent via the connection will be logged
* `DownloadLogger.responses`: (optional) if present, data received via the connection will be logged

## AssignmentRemover component

This component will remove any assignments with the specified result type. Note that Jimple does not support nested expressions, so any intermediate result is assigned to a local variable.

Configuration options:

* `AssignmentRemover.enabled`: add to enable this component
* `AssignmentRemover.filter`: (optional) restricts functionality to a set of classes or methods (see Filters section above)
* `AssignmentRemover.type`: the result type identifying the assignment to be removed
