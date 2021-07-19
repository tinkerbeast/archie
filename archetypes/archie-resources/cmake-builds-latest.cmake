# === Coverage build =========================================================

option(ARCHIE_COVERAGE "Enable coverage reporting" OFF)
if(ARCHIE_COVERAGE) 
    find_program(GCOV_PATH gcov)
    find_program(LCOV_PATH NAMES lcov lcov.bat lcov.exe lcov.perl)
    if(NOT GCOV_PATH)
        message(FATAL_ERROR "ARCHIE: gcov not found")
    endif()
    if(NOT LCOV_PATH)
        message(WARNING "ARCHIE: lcov is needed to run coverage target")
    endif()
    if(CMAKE_CXX_COMPILER_ID MATCHES "GNU|Clang" AND CMAKE_BUILD_TYPE MATCHES "Debug")
        # Debug option should set -O0 and -g flags necessary for coverage
        # TODO(rishin): What if this changes in cmake in future?
        message(STATUS "ARCHIE: Building with coverage enabled")
    else()
        message(FATAL_ERROR "ARCHIE: Conflicting build options with coverage enables")
    endif()
endif()

if(ARCHIE_COVERAGE AND LCOV_PATH)
    add_custom_command(OUTPUT coverage.info
            COMMAND lcov --directory ${CMAKE_CURRENT_BINARY_DIR} --capture --output-file coverage.info
            COMMAND lcov --remove coverage.info '/usr/*' --output-file coverage.info
            COMMAND lcov --list coverage.info
        )
    add_custom_target(coverage DEPENDS coverage.info)
endif()


# === Build functions ========================================================

macro(archie_cxx_deps_segregate shared_libs interface_libs)
  # TODO: remove this print
  #message(STATUS "shared_libs=${shared_libs} interface_libs=${interface_libs} deps_public=${ARGN}")
  set(deps_public ${ARGN})
  # Check whether a shared library is shared or interface
  set(${shared_libs} )
  set(${interface_libs} )
  foreach(dep ${deps_public})
    get_target_property(dep_type dep)
    if(dep_type STREQUAL "SHARED_LIBRARY")
      list(APPEND ${shared_libs} dep)
    elseif(dep_type STREQUAL "INTERFACE_LIBRARY")
      list(APPEND ${interface_libs} dep)
    else()
      message(FATAL_ERROR "${dep} needs to be shared or header libraries")
    endif()
  endforeach(dep)
endmacro()

# cc_shared_library(namespace target
#                   SRCS ...
#                   INCL_PRIV ...
#                   INCL_PUBL ...
#                   DEPS_PRIV ...
#                   DEPS_PUBL ...
# )
function(archie_cxx_library_shared namespace target)
  # See: https://cmake.org/cmake/help/latest/command/cmake_parse_arguments.html
  set(multiValueArgs SRCS INCL_PRIV INCL_PUBL DEPS_PRIV DEPS_PUBL)
  cmake_parse_arguments(ARCHIE_CXX_LIB "" "" "${multiValueArgs}" ${ARGN} )
  # Make shared library target
  if(NOT ARCHIE_CXX_LIB_SRCS)
    message(FATAL_ERROR "archie_cxx_shared_library needs SRCS parameters")
  endif()
  set(lib_name "${namespace}-${target}")
  add_library(${lib_name} SHARED ${ARCHIE_CXX_LIB_SRCS})
  target_compile_options(${lib_name} PRIVATE ${ARCHIE_ERROR_FLAGS})
  # Coverage related flags
  if(ARCHIE_COVERAGE)
    target_compile_options(${lib_name} PRIVATE --coverage)
    target_link_options("${lib_name}" PRIVATE --coverage)
  endif()
  # Add include directories
  if(ARCHIE_CXX_LIB_INCL_PRIV)
    target_include_directories(${lib_name} PRIVATE ${ARCHIE_CXX_LIB_INCL_PRIV})
  endif()
  if(ARCHIE_CXX_LIB_INCL_PUBL)
    target_include_directories(${lib_name} PUBLIC ${ARCHIE_CXX_LIB_INCL_PUBL})
  endif()
  # Add private dependencies
  if(ARCHIE_CXX_LIB_DEPS_PRIV)
    target_link_libraries(${lib_name} PRIVATE ${ARCHIE_CXX_LIB_DEPS_PRIV})
  endif()
  # Add public dependencies
  archie_cxx_deps_segregate(shared_libs interface_libs ${ARCHIE_CXX_LIB_DEPS_PUBL})
  if(shared_libs)
    target_link_libraries(${lib_name} PUBLIC ${shared_libs})
  endif()
  if(interface_libs)
    target_link_libraries(${lib_name} INTERFACE ${interface_libs})
  endif()
  # Alias the library to have a namespace
  add_library(${namespace}::${target} ALIAS ${lib_name})
endfunction()


function(archie_cxx_executable namespace target)
  # See: https://cmake.org/cmake/help/latest/command/cmake_parse_arguments.html
  set(options EXCLUDE_FROM_ALL)
  set(multiValueArgs SRCS DEPS_PRIV DEPS_PUBL)
  cmake_parse_arguments(ARCHIE_CXX_EXE "${options}" "" "${multiValueArgs}" ${ARGN} )
  # Make executable target
  if(NOT ARCHIE_CXX_EXE_SRCS)
    message(FATAL_ERROR "archie_cxx_executable needs SRCS parameter")
  endif()
  set(exec_name "${namespace}-${target}")
  if(NOT ARCHIE_CXX_EXE_EXCLUDE_FROM_ALL)
    add_executable(${exec_name} ${ARCHIE_CXX_EXE_SRCS})
  else()
    add_executable(${exec_name} EXCLUDE_FROM_ALL ${ARCHIE_CXX_EXE_SRCS})
  endif()
  target_compile_options(${exec_name} PRIVATE ${ARCHIE_ERROR_FLAGS})
  # Add private dependencies
  if(ARCHIE_CXX_EXE_DEPS_PRIV)
    target_link_libraries(${exec_name} PRIVATE ${ARCHIE_CXX_EXE_DEPS_PRIV})
  endif()
  # Add public dependencies
  archie_cxx_deps_segregate(shared_libs interface_libs ${ARCHIE_CXX_EXE_DEPS_PUBL})
  if(shared_libs)
    target_link_libraries(${exec_name} PUBLIC ${shared_libs})
  endif()
  if(interface_libs)
    target_link_libraries(${exec_name} INTERFACE ${interface_libs})
  endif()
endfunction()
  
if(BUILD_TESTING)
    FetchContent_Declare(
      googletest
      GIT_REPOSITORY    "https://github.com/google/googletest"
      GIT_TAG           "release-1.8.0"
      GIT_SHALLOW       ON
    )
    # TODO(rishin): Change this to FetchContent_MakeAvailable and update cmake version to 3.14
    FetchContent_GetProperties(googletest)
    if(NOT googletest_POPULATED)
        FetchContent_Populate(googletest)
        # add the targets: gtest,gtest_main,gmock,gmock_main
        add_subdirectory(${googletest_SOURCE_DIR} ${googletest_BINARY_DIR} EXCLUDE_FROM_ALL)
    endif()

    include(CTest)
endif()

if(BUILD_TESTING)
    
  function(archie_cxx_test namespace target)
    if(NOT TARGET test-build)
        add_custom_target(test-build)
    endif()
    archie_cxx_executable(${namespace} ${target} EXCLUDE_FROM_ALL ${ARGN})
    target_link_libraries("${namespace}-${target}" PRIVATE gtest_main)
    add_dependencies(test-build "${namespace}-${target}")
    add_test(NAME "${namespace}:${target}" COMMAND "${namespace}-${target}")
  endfunction()

endif()
