# === Define defaults ========================================================

# Default build type
if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE "Debug")
endif()

# TODO(rishin): Parametrise C++ standard
# Default cpp version
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Default build flags
if(CMAKE_CXX_COMPILER_ID MATCHES "GNU")
    # error options
    # See https://stackoverflow.com/questions/5088460/flags-to-enable-thorough-and-verbose-g-warnings
    # See https://stackoverflow.com/questions/399850/best-compiler-warning-level-for-c-c-compilers/401276#401276
    set(ARCHIE_ERROR_FLAGS -pedantic -pedantic-errors -Wall -Werror -Wcast-align -Wcast-qual -Wconversion -Wdisabled-optimization -Wextra -Wfloat-equal -Wformat=2 -Wformat-nonliteral -Wformat-security -Wformat-y2k -Wimport -Winit-self -Winline -Winvalid-pch -Wmissing-field-initializers -Wmissing-format-attribute -Wmissing-include-dirs -Wmissing-noreturn -Wnon-virtual-dtor -Wpacked -Wpointer-arith -Wredundant-decls -Wsign-conversion -Wstack-protector -Wstrict-aliasing=2 -Wunreachable-code -Wunused -Wunused-parameter -Wvariadic-macros -Wwrite-strings -Wswitch-default -Wswitch-enum -Wdeprecated-copy-dtor -Wunused-value -Wshadow)
    # perf related
    list(APPEND ARCHIE_ERROR_FLAGS -fno-omit-frame-pointer)
    # security related
    # TODO(rishin): Make fortify source level 2 work cmake
    # TODO(rishin): -DCMAKE_POSITION_INDEPENDENT_CODE
    list(APPEND ARCHIE_ERROR_FLAGS -fPIE -fPIC -D_FORTIFY_SOURCE=1)
endif()
# TODO(rishin): add flags for Clang
# TODO(rishin): add flags for VC++

# Default miscellaneous settings
find_program(CCACHE_FOUND ccache) # Integration with ccahe.
if(CCACHE_FOUND)
    set_property(GLOBAL PROPERTY RULE_LAUNCH_COMPILE ccache)
    # ccache linking is disabled by default since it does not improve linking
    # speed and meses with other caches
    #set_property(GLOBAL PROPERTY RULE_LAUNCH_LINK ccache)
endif(CCACHE_FOUND)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON) # Needed for third-party tools like vim and mull

# === Default includes =======================================================

include(FetchContent)
set(FETCHCONTENT_QUIET OFF)
