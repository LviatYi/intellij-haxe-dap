# Haxe plugin for Intellij IDEA (Debug Adapter Protocol Support)

Forked from [intellij-haxe | Github][path_to_origin_repo].

Implemented a debugger based on the DAP protocol to connect to the hxcpp debugging server.

Credit By [LviatYi](mailto:LviatYi@foxmail.com).

---

This project is based on [intellij-haxe][path_to_origin_repo] and aims to provide debugging capabilities based on the dap protocol and try
to optimize additional functions.

The [original plugin][path_to_origin_repo] allows you to develop multi-platform programs using the [Haxe](http://haxe.org/) language
with [Intellij IDEA](http://www.jetbrains.com/idea), [Android Studio](https://developer.android.com/studio/) and other IntelliJ IDEA-based
IDEs by JetBrains.
It requires Intellij IDEA Ultimate or Community Edition 2023 or later, or Android Studio Giraffe or later.

However, due to limited knowledge and energy, this project only provides guarantees available on IDEA.

## Functional

- [x] Debugging based on the DAP protocol.
- [ ] Optimizing debugger performance.

## Install

There is no download method from the idea plug-in market yet. You can build your own.

## Build

This describes the command line build.  To build from within Intellij IDEA itself, see the [contributing](CONTRIBUTING.md) document to setup
your development environment.  Much more detail is provided there for command line build options as well.

### Dependencies

- OpenJDK 17
- A windows command prompt or bash compatible shell

### Build command

Windows
```
gradlew.bat clean build verifyPlugin 
```
Mac/Linux
```
./gradlew clean build verifyPlugin 
```

>NOTE: You can run the build without tests by substituting `build` with `buildPlugin` on the lines above

This will generate a `intelllij-haxe-<release>.jar` file at the root of the project that you can then install from disk
(see “Install the latest or a previous Github release).

Note that the first time you build the project Gradle will download the requested version of IntelliJ Ultimate and 
any other other dependencies. This can be quite slow at times and prone to failure.  For repeated building and testing,
we recommended that you set up your machine as described in the [contributing document](CONTRIBUTING.md).

## Use the hxcpp debugger

First, the [original plugin][path_to_origin_repo] does not support haxe debugging in idea community edition.
The project has been modified so that this limitation is removed. However, this has not been tested on Android Studio or other IntelliJ
IDEA-based
IDEs.

Secondly, the project supports hxcpp debugger based on Debugger Adapter Protocol (DAP). The debugger server of this protocol version can be
used in vscode. This project adapts it to IDEA.

To use this feature, you must:

- install hxcpp-debug-server from https://lib.haxe.org/p/hxcpp-debug-server/
  - or use `haxelib install hxcpp-debug-server 1.2.4`
- Re-build your project with this haxelib.

## Contribute

See the [contributing document](CONTRIBUTING.md) for more information.

[path_to_origin_repo]:http://github.com/HaxeFoundation/intellij-haxe

[path_to_repo]:https://github.com/LviatYi/intellij-haxe-dap

[path_to_repo_release]:https://github.com/LviatYi/intellij-haxe-dap/releases

[path_to_repo_issue]: https://github.com/LviatYi/intellij-haxe-dap/issues