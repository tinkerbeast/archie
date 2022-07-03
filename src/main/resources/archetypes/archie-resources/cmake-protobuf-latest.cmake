# TODO: see https://github.com/protocolbuffers/protobuf/blob/master/examples/CMakeLists.txt
# TODO: https://github.com/grpc/grpc/tree/v1.38.0/examples/cpp/route_guide
# https://grpc.io/docs/languages/python/basics/
find_package(Protobuf REQUIRED)
function(archie_add_proto_lib_file lib_name proto_file)
    protobuf_generate_cpp(PROTO_SRCS PROTO_HDRS ${proto_file})
    #message(STATUS "PROTO_SRCS=${PROTO_SRCS}")
    #message(STATUS "PROTO_HDRS=${PROTO_HDRS}")
    #message(STATUS "PROTOBUF_INCLUDE_DIRS=${PROTOBUF_INCLUDE_DIRS}")
    add_library(${lib_name} SHARED ${PROTO_SRCS}) # TODO(rishin): Remove from all target
    target_compile_options(${lib_name} PRIVATE ${ARCHIE_ERROR_FLAGS})
    #target_include_directories(${lib_name} PUBLIC ${PROTOBUF_INCLUDE_DIRS}) # TODO: is this really necessary?
    target_link_libraries(${lib_name} protobuf::libprotobuf)
    target_include_directories(${lib_name} PUBLIC ${CMAKE_CURRENT_BINARY_DIR}) # TODO: how do i arrive a the target directory? 
endfunction()

find_program(GRPC_CPP_PLUGIN_PATH grpc_cpp_plugin)
find_package(PkgConfig REQUIRED)
pkg_search_module(gRPC REQUIRED grpc++)
# TODO: build grpc from source
#FetchContent_Declare(
#  gRPC
#  GIT_REPOSITORY https://github.com/grpc/grpc
#  GIT_TAG        v1.28.1
#  GIT_SHALLOW    ON
#)
#FetchContent_MakeAvailable(gRPC)

function(archie_add_protogrpc_lib_file lib_name proto_file)
    get_filename_component(proto_path "${proto_file}" PATH)
    get_filename_component(proto_name_with_ext ${proto_file} NAME)
    get_filename_component(proto_name ${proto_name_with_ext} NAME_WE)
    add_custom_command(
      OUTPUT "${proto_name}.grpc.pb.cc" "${proto_name}.grpc.pb.h"
      COMMAND protobuf::protoc
      ARGS --grpc_out "${CMAKE_CURRENT_BINARY_DIR}"
        -I "${proto_path}"
        --plugin=protoc-gen-grpc="${GRPC_CPP_PLUGIN_PATH}"
        "${proto_file}"
      DEPENDS "${proto_file}")
    add_library(${lib_name} SHARED "${proto_name}.grpc.pb.cc" "${proto_name}.grpc.pb.h") # TODO(rishin): Remove from all target
    target_compile_options(${lib_name} PRIVATE ${ARCHIE_ERROR_FLAGS})
    target_link_libraries(${lib_name} grpc++)
    # TODO(rishin): add header path
endfunction()

