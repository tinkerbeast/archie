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

# === Library and executable build ===========================================

function(archie_add_cxx_lib_dir lib_name lib_dir)
    # add all files in the subdirectory
    file(GLOB lib_srcs ${lib_dir}/*.cpp ${lib_dir}/*.cc)
    list(FILTER lib_srcs EXCLUDE REGEX "^.*test\\.(cpp|cc)$")
    list(FILTER lib_srcs EXCLUDE REGEX "^.*main\\.(cpp|cc)$")
    # make a dynamic library
    add_library(${lib_name} SHARED ${lib_srcs}) # TODO(rishin): Remove from all target
    target_compile_options(${lib_name} PRIVATE ${ARCHIE_ERROR_FLAGS})
    target_include_directories(${lib_name} PRIVATE ${lib_dir}) # TODO(rishin): Test if subdirectory works with private
    if(ARCHIE_COVERAGE)
        target_compile_options(${lib_name} PRIVATE --coverage)
        target_link_options("${lib_name}" PRIVATE --coverage)
    endif()
endfunction()

function(archie_add_cxx_exec_file exec_name exec_file)
    add_executable(${exec_name} ${exec_file})
    target_compile_options(${exec_name} PRIVATE ${ARCHIE_ERROR_FLAGS})
endfunction()

# === Unit tests build =======================================================

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

    function(archie_add_cxx_test_dir test_namespace test_dir lib_dependencies)
        add_custom_target(${test_namespace})
        # add all files in the subdirectory
        file(GLOB test_srcs ${test_dir}/*.cpp ${test_dir}/*.cc)
        list(FILTER test_srcs INCLUDE REGEX "^.*test\\.(cpp|cc)$")
        # make a dynamic library
        foreach(file_path ${test_srcs})
            # Strip file path to just file name.
            get_filename_component(file_name_with_ext ${file_path} NAME)
            get_filename_component(file_name ${file_name_with_ext} NAME_WE)
            # Add the test target.
            add_executable("${test_namespace}-${file_name}" EXCLUDE_FROM_ALL ${file_path})
            add_dependencies(${test_namespace} "${test_namespace}-${file_name}")
            target_link_libraries("${test_namespace}-${file_name}" PRIVATE gtest_main ${lib_dependencies})
            add_test(NAME "${test_namespace}:${file_name}" COMMAND "${test_namespace}-${file_name}")
        endforeach(file_path)
    endfunction()
endif()
