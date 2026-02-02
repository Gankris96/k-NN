# Distributed under the OSI-approved BSD 3-Clause License.  See accompanying
# file LICENSE.rst or https://cmake.org/licensing for details.

cmake_minimum_required(VERSION ${CMAKE_VERSION}) # this file comes with cmake

# If CMAKE_DISABLE_SOURCE_CHANGES is set to true and the source directory is an
# existing directory in our source tree, calling file(MAKE_DIRECTORY) on it
# would cause a fatal error, even though it would be a no-op.
if(NOT EXISTS "/Volumes/workplace/opensource/k-NN/jni/googletest-src")
  file(MAKE_DIRECTORY "/Volumes/workplace/opensource/k-NN/jni/googletest-src")
endif()
file(MAKE_DIRECTORY
  "/Volumes/workplace/opensource/k-NN/jni/googletest-build"
  "/Volumes/workplace/opensource/k-NN/jni/googletest-download/googletest-prefix"
  "/Volumes/workplace/opensource/k-NN/jni/googletest-download/googletest-prefix/tmp"
  "/Volumes/workplace/opensource/k-NN/jni/googletest-download/googletest-prefix/src/googletest-stamp"
  "/Volumes/workplace/opensource/k-NN/jni/googletest-download/googletest-prefix/src"
  "/Volumes/workplace/opensource/k-NN/jni/googletest-download/googletest-prefix/src/googletest-stamp"
)

set(configSubDirs )
foreach(subDir IN LISTS configSubDirs)
    file(MAKE_DIRECTORY "/Volumes/workplace/opensource/k-NN/jni/googletest-download/googletest-prefix/src/googletest-stamp/${subDir}")
endforeach()
if(cfgdir)
  file(MAKE_DIRECTORY "/Volumes/workplace/opensource/k-NN/jni/googletest-download/googletest-prefix/src/googletest-stamp${cfgdir}") # cfgdir has leading slash
endif()
