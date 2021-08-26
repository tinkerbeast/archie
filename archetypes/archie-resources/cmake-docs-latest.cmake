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
  # Add the docs target
  doxygen_add_docs(docs
      "include" "src"
      COMMENT "ARCHIE: Doxygen found and custom target `docs` added")
else()
  message(WARNING "ARCHIE: Doxygen not found, so targe `docs` will not be available")
endif()
