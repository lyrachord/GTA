# GTA
simple build too for java 9 module, where GTA stands for Greater than 8(A) and means it must run at jav 9 and above

# Requirement
1. JDK9
2. change the path of java.exe in gta.bat to your JDK9 path. NOTE: there is no need for java_home env variable, because the bat/sh

..* for *nix user create a gta.sh is apoc:)

# Directory struct
```
project
├─mods
├─src
│  ├─com.fastsocket
│  │  ├─module-info.java
│  │  └─com
│  │      └─fastsocket
│  ├─com.greetings
│  │  ├─module-info.java
│  │  └─com
│  │      └─greetings
│  ├─com.socket
│  │  ├─module-info.java
│  │  └─com
│  │      └─socket
│  │          └─spi
│  └─org.astro
│      ├─module-info.java
│      └─org
│          └─astro
```


# Usage
gta [target [target]]
default target is build
## list
  list all modules
## compile 
  automatically build all modules
## clean
  clean all builds
## main
  list all MainClass
## run [module/]MainClass
  run the given module/Mainclass, when 'module/' part ignored, the tool automatically matches a scanned MainClass
