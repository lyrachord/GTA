# GTA
simple build too for java 9 module, where GTA stands for Greater than 8(A) and means must run at jav 9 and above

# Directory struct
TODO:

# Usage
gta [target [target]]
default target is build
## list
  list all modules
## compile [module [module]]
  automatically build all modules, dependencies must be taken care
## main
  list all MainClass
## run [module/]MainClass
  run the given module/Mainclass, when module/ part ignored, the tool automatically matches scanned MainClass
## jar
  package all modules by jar format
## mod
  package all modules by jmod format
## clean
  clean all builds
