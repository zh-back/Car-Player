cmake_minimum_required(VERSION 3.10.2)

project("caraudio")

add_library(caraudio SHARED
    native-audio.c
    dsp_engine.c
)

find_library(log-lib log)
find_library(aaudio-lib aaudio)
find_library(math-lib m)

target_link_libraries(caraudio
    ${log-lib}
    ${aaudio-lib}
    ${math-lib}
)
