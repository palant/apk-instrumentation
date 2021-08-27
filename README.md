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
* `platformVersion`: (optional) platform version to be loaded in Android SDK. If omitted, will be detected automatically.
* `input`: path to the input APK file
* `output`: path of the instrumented APK file to be written
* `keystore`: (optional) path to the key store containing the signing key
* `keypass`: (optional) password protecting the key store

## Method filters

Some configuration options use method filters determining which methods are included. These are space-separated lists, entries can have the following format:

* `com.example.test.*`: includes all classes with names matching a particular prefix
* `com.example.test.Main:*`: includes all methods of a specific class
* `com.example.test.Main:dump(java.lang.String,int)`: includes only the method with the specified signature

## Extended format strings

Some components will allow specifying extended format strings for data to be logged. These use the usual [Java format specifiers](https://docs.oracle.com/javase/7/docs/api/java/util/Formatter.html#syntax) like `%s` or `%i` but require specifying the input as well, e.g. `{this:%s}` (format `this` value as a string) or `{arg2:%i}` (format second parameter as integer). The following input specifiers are possible:

* `method`: The signature of the calling method
* `result`: Call result if available
* `this`: Instance reference
* `argNN`: Argument value where NN is the argument’s zero-based position
* `args`: Comma-separated list of all arguments (stringified)

In addition, the format specifier `%x` is treated specially: `System.identityHashCode()` will be called on the corresponding input and the result hex-formatted.

## CallLogger component

This component will add logging code after calls to specified methods. See `config.properties.downloads` for a configuration example logging `URLConnection` interactions.

Configuration options:

* `CallLogger.enabled`: add to enable this component
* `CallLogger.filter`: (optional) restricts functionality to a set of methods, for value format see Method filters section above
* `CallLogger.tag`: (optional) log tag to be used (default is `CallLogger`)
* `CallLogger.<method filter>`: specifies that calls to the specified method should be logged. `<method filter>` is a method specification as outlined in the Method filters section above. Note that `.properties` format requires colons to be prefixed with a backslash: `\:`. The value is a format string (see Extended format strings section above).

## StreamLogger component

This component will wrap `InputStream` and `OutputStream` instances returned by specified methods to log data being sent or received. See `config.properties.downloads` for a configuration example logging streams returned by `URLConnection.getInputStream()` and `URLConnection.getOutputStream()`.

Configuration options:

* `StreamLogger.enabled`: add to enable this component
* `StreamLogger.filter`: (optional) restricts functionality to a set of methods, for value format see Method filters section above
* `StreamLogger.tag`: (optional) log tag to be used (default is `StreamLogger`)
* `StreamLogger.<method filter>`: specifies a call returning a stream that should be wrapped. `<method filter>` is a method specification as outlined in the Method filters section above. Note that `.properties` format requires colons to be prefixed with a backslash: `\:`. The value is a format string that will be used as a prefix for logged data (see Extended format strings section above).

## MethodLogger component

This component will add logging to the start of a method. This might be more efficient than `CallLogger` for methods called from many places, and it will log even calls resulting in an exception. The disadvantage is that the method’s return value cannot be logged as it isn’t known at this stage.

Configuration options:

* `MethodLogger.enabled`: add to enable this component
* `MethodLogger.tag`: (optional) log tag to be used (default is `MethodLogger`)
* `MethodLogger.<method filter>`: specifies a method that should be logged. `<method filter>` is a method specification as outlined in the Method filters section above. Note that `.properties` format requires colons to be prefixed with a backslash: `\:`. The value is a format string like `Entered method {method:%s} ({args:%s})` (see Extended format strings section above).

## AssignmentRemover component

This component will remove any assignments with the specified result type. Note that Jimple does not support nested expressions, so any intermediate result is assigned to a local variable.

Configuration options:

* `AssignmentRemover.enabled`: add to enable this component
* `AssignmentRemover.filter`: (optional) restricts functionality to a set of methods, for value format see Method filters section above
* `AssignmentRemover.type`: the result type identifying the assignment to be removed

## CallRemover component

This component will remove any calls to the specified method(s).

Configuration options:

* `CallRemover.enabled`: add to enable this component
* `CallRemover.filter`: (optional) restricts functionality to a set of methods, for value format see Method filters section above
* `CallRemover.method`: specifies the method(s) to be removed, for value format see Method filters section above
