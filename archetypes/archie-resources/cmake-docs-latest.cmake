find_package(Doxygen)
if(DOXYGEN_FOUND)
  # Project related configuration options
  set(DOXYGEN_FULL_PATH_NAMES NO)
  set(DOXYGEN_OPTIMIZE_OUTPUT_FOR_C YES)
  set(DOXYGEN_BUILTIN_STL_SUPPORT YES)
  # Build related configuration options
  set(DOXYGEN_EXTRACT_ALL YES)
  set(DOXYGEN_EXTRACT_PRIVATE YES)
  set(DOXYGEN_EXTRACT_PRIV_VIRTUAL YES)
  set(DOXYGEN_EXTRACT_STATIC YES)
  # Configuration options related to the HTML and XML output
  set(DOXYGEN_GENERATE_HTML YES)
  set(DOXYGEN_GENERATE_XML YES)
else()
  message(WARNING "ARCHIE: Doxygen not found, so target `docs` will not be available")
endif()


# find_package(Archie_Sphinx) -- BEGIN


# Find sphinx related executables.
find_program(SPHINX_EXECUTABLE
             NAMES sphinx-build sphinx-build2
             DOC "Path to sphinx-build executable")
mark_as_advanced(SPHINX_EXECUTABLE)

# Check if all necessary plugins are installed.
if(SPHINX_EXECUTABLE)
  # Need python interpreter to determine if sphinx plugins are installed.
  find_package(Python COMPONENTS Interpreter)
  if(Python_FOUND)
    execute_process(COMMAND ${Python_EXECUTABLE} "-c" "import breathe"
        RESULT_VARIABLE SPHINX_BREATHE_FOUND)
    execute_process(COMMAND ${Python_EXECUTABLE} "-c" "import myst_parser"
        RESULT_VARIABLE SPHINX_MYST_FOUND)
    if(SPHINX_BREATHE_FOUND STREQUAL "0")
      set(SPHINX_BREATHE_FOUND TRUE)
    else()
      set(SPHINX_BREATHE_FOUND FALSE)
    endif()
    if(SPHINX_MYST_FOUND STREQUAL "0")
      set(SPHINX_MYST_FOUND TRUE)
    else()
      set(SPHINX_MYST_FOUND FALSE)
    endif()
  endif()  
endif()

# Set Sphinx version.
if(SPHINX_EXECUTABLE)
  execute_process(COMMAND ${SPHINX_EXECUTABLE} "--version"
      OUTPUT_VARIABLE _SPHINX_VERSION
      RESULT_VARIABLE SPHINX_VERSION_RESULT)
  if(NOT SPHINX_VERSION_RESULT)
    string(SUBSTRING ${_SPHINX_VERSION} 13 -1 SPHINX_VERSION)
  endif()
endif()

# Set plugin list.
if(NOT DEFINED SPHINX_PLUGINS)
  set(SPHINX_PLUGINS "myst_parser")
else()
  list(APPEND SPHINX_PLUGINS "myst_parser")
endif()

# Set ARCHIE_SPHINX_FOUND to true if dependencies exist.
include(FindPackageHandleStandardArgs)
if(DOXYGEN_FOUND)
  # Breathe plugin only works with Doxygen.
  find_package_handle_standard_args(
      Archie_Sphinx
      REQUIRED_VARS SPHINX_EXECUTABLE SPHINX_MYST_FOUND SPHINX_BREATHE_FOUND
      VERSION_VAR ${SPHINX_VERSION})

  list(APPEND SPHINX_PLUGINS "breathe")
else()
  find_package_handle_standard_args(
      Archie_Sphinx
      REQUIRED_VARS SPHINX_EXECUTABLE SPHINX_MYST_FOUND
      VERSION_VAR ${SPHINX_VERSION})
endif()

# Create targets.
if(ARCHIE_SPHINX_FOUND)
  if(NOT TARGET Archie_Sphinx::build)
    add_executable(Archie_Sphinx::build IMPORTED GLOBAL)
    set_target_properties(Archie_Sphinx::build PROPERTIES
        IMPORTED_LOCATION "${SPHINX_EXECUTABLE}")
  endif()
endif()

function(archie_sphinx_add_docs targetName)
  # Create argument parser.
  set(_options ALL)
  set(_one_value_args WORKING_DIRECTORY COMMENT)
  set(_multi_value_args)
  cmake_parse_arguments(_args
                        "${_options}"
                        "${_one_value_args}"
                        "${_multi_value_args}"
                        ${ARGN})
  # Parse arguments.
  if(NOT _args_WORKING_DIRECTORY)
    set(_args_WORKING_DIRECTORY "${CMAKE_CURRENT_SOURCE_DIR}")
  endif()

  # TODO: _args_COMMENT is unused (see doxygen_add_docs implementation)
  if(NOT _args_COMMENT)
    set(_args_COMMENT "Generate site documentation for ${targetName}")
  endif()
  unset(_all)
  if(${_args_ALL})
    set(_all ALL)
  endif()
  set(_sphinx_input ${_args_UNPARSED_ARGUMENTS})
  # Check if dependencies are met or raise error.
  if(NOT TARGET Archie_Sphinx::build)
    message(FATAL_ERROR "Sphinx was not found, needed by archie_sphinx_add_docs() for target ${targetName}")
  endif()
  # Set default values for sphinx parameters.
  if(NOT DEFINED SPHINX_PROJECT_NAME)
    set(SPHINX_PROJECT_NAME ${PROJECT_NAME})
  endif()
  if(NOT DEFINED SPHINX_AUTHOR_NAME)
    set(SPHINX_AUTHOR_NAME "")
  endif()
  if(NOT DEFINED SPHINX_SUFFIX)
    set(SPHINX_SUFFIX ".md")
  endif()
  # Create dummy target which always rebuilds.
  add_custom_command(
    OUTPUT always_rebuild
    COMMAND ${CMAKE_COMMAND} -E echo)
  # Create target to generate the files.
  set(_sphinx_target_dir "${CMAKE_CURRENT_BINARY_DIR}/sphinx-${targetName}")
  set_property(DIRECTORY APPEND PROPERTY
      ADDITIONAL_CLEAN_FILES "${_sphinx_target_dir}")
  if(IS_ABSOLUTE "${_sphinx_input}")
    set(_sphinx_input "${_sphinx_input}")
  else()
    set(_sphinx_input "${CMAKE_CURRENT_SOURCE_DIR}/${_sphinx_input}")
  endif()

  if(DOXYGEN_FOUND)
    add_custom_command(
        OUTPUT "${_sphinx_target_dir}"
        COMMAND ${SPHINX_EXECUTABLE} "${_sphinx_input}" "${_sphinx_target_dir}" "-D" "'breathe_projects.${PROJECT_NAME}'='${CMAKE_CURRENT_BINARY_DIR}/xml'"
        DEPENDS always_rebuild docs
        WORKING_DIRECTORY ${_args_WORKING_DIRECTORY}
     )
  else()
    add_custom_command(
        OUTPUT "${_sphinx_target_dir}"
        COMMAND ${SPHINX_EXECUTABLE} "${_sphinx_input}" "${_sphinx_target_dir}"
        DEPENDS always_rebuild
        WORKING_DIRECTORY ${_args_WORKING_DIRECTORY}
     )
  endif()
  add_custom_target(${targetName} ${_all}
      DEPENDS "${_sphinx_target_dir}")
endfunction()

# find_package(Archie_Sphinx) -- END


if(ARCHIE_SPHINX_FOUND)
  message(COMMENT "ARCHIE: Sphinx found and custom target `site` added")
else()
  message(WARNING "ARCHIE: Sphinx not found, so target `site` will not be available")
endif()

