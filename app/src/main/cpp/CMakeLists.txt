
# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.18.1)

project("attysecg")

set(OCULUS_MOBILE_SDK /home/bp1/oculus)

include_directories(
        ${OCULUS_MOBILE_SDK}/VrApi/Include
        ${OCULUS_MOBILE_SDK}/1stParty/utilities/include/
        ${OCULUS_MOBILE_SDK}/1stParty/OVR/Include
        ${OCULUS_MOBILE_SDK}/3rdParty/minizip/src
        ${OCULUS_MOBILE_SDK}/3rdParty/stb/src
        ${OCULUS_MOBILE_SDK}/SampleFramework/Src
        ${OCULUS_MOBILE_SDK}/SampleCommon/Src
        ${ANDROID_NDK}/sources/android/native_app_glue
)

option(BUILD_LOADER "Build loader" OFF)

add_library(vrapi SHARED IMPORTED)

set_property(
        TARGET
        vrapi
        PROPERTY
        IMPORTED_LOCATION
        ${OCULUS_MOBILE_SDK}/VrApi/Libs/Android/${ANDROID_ABI}/${CMAKE_BUILD_TYPE}/libvrapi.so
)

add_library(minizip STATIC
        ${OCULUS_MOBILE_SDK}/3rdParty/minizip/src/ioapi.c
        ${OCULUS_MOBILE_SDK}/3rdParty/minizip/src/mztools.c
        ${OCULUS_MOBILE_SDK}/3rdParty/minizip/src/iowin32.c
        ${OCULUS_MOBILE_SDK}/3rdParty/minizip/src/unzip.c
        ${OCULUS_MOBILE_SDK}/3rdParty/minizip/src/miniunz.c
        ${OCULUS_MOBILE_SDK}/3rdParty/minizip/src/zip.c
        ${OCULUS_MOBILE_SDK}/3rdParty/minizip/src/minizip.c
        )

add_library(stb STATIC
        ${OCULUS_MOBILE_SDK}/3rdParty/stb/src/stb_image.c
        ${OCULUS_MOBILE_SDK}/3rdParty/stb/src/stb_image_write.c
        ${OCULUS_MOBILE_SDK}/3rdParty/stb/src/stb_vorbis.c
        )

add_library(sampleframework STATIC
        ${OCULUS_MOBILE_SDK}/SampleFramework/Src/Appl.cpp
        ${OCULUS_MOBILE_SDK}/SampleFramework/Src/Input/HandMaskRenderer.cpp
        ${OCULUS_MOBILE_SDK}/SampleFramework/Src/Input/HandModel.cpp
        ${OCULUS_MOBILE_SDK}/SampleFramework/Src/Input/HandRenderer.cpp
        ${OCULUS_MOBILE_SDK}/SampleFramework/Src/Platform/Android/Android.cpp
        ${OCULUS_MOBILE_SDK}/SampleFramework/Src/Render/Framebuffer.c
        ${OCULUS_MOBILE_SDK}/SampleFramework/Src/SurfaceRenderApp.cpp
        )

add_library(samplecommon STATIC
${OCULUS_MOBILE_SDK}/SampleCommon/Src/GUI/ActionComponents.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/GUI/AnimComponents.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/GUI/CollisionPrimitive.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/GUI/DefaultComponent.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/GUI/Fader.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/GUI/GazeCursor.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/GUI/GuiSys.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/GUI/MetaDataManager.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/GUI/Reflection.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/GUI/ReflectionData.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/GUI/SoundLimiter.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/GUI/VRMenu.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/GUI/VRMenuComponent.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/GUI/VRMenuEvent.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/GUI/VRMenuEventHandler.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/GUI/VRMenuMgr.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/GUI/VRMenuObject.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/Input/ArmModel.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/Input/AxisRenderer.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/Input/ControllerRenderer.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/Input/Skeleton.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/Input/SkeletonRenderer.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/Input/TinyUI.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/Locale/OVR_Locale.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/Locale/tinyxml2.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/Misc/Log.c
${OCULUS_MOBILE_SDK}/SampleCommon/Src/Model/ModelCollision.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/Model/ModelFile_glTF.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/Model/ModelFile_OvrScene.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/Model/ModelFile.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/Model/ModelRender.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/Model/ModelTrace.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/Model/SceneView.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/OVR_BinaryFile2.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/OVR_FileSys.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/OVR_Lexer2.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/OVR_MappedFile.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/OVR_Stream.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/OVR_Uri.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/OVR_UTF8Util.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/PackageFiles.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/Render/BeamRenderer.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/Render/BitmapFont.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/Render/DebugLines.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/Render/EaseFunctions.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/Render/Egl.c
${OCULUS_MOBILE_SDK}/SampleCommon/Src/Render/GeometryBuilder.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/Render/GeometryRenderer.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/Render/GlBuffer.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/Render/GlGeometry.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/Render/GlProgram.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/Render/GlSetup.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/Render/GlTexture.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/Render/PanelRenderer.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/Render/ParticleSystem.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/Render/PointList.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/Render/Ribbon.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/Render/SurfaceRender.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/Render/SurfaceTexture.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/Render/TextureAtlas.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/Render/TextureManager.cpp
${OCULUS_MOBILE_SDK}/SampleCommon/Src/System.cpp
        )

add_library( # Sets the name of the library.
             attysecg

             # Sets the library as a shared library.
             SHARED

             # Provides a relative path to your source file(s).
             VrAttsECG_Framework.cpp)

# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

find_library( # Sets the name of the path variable.
              log-lib

              # Specifies the name of the NDK library that
              # you want CMake to locate.
              log )

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.

target_link_libraries( # Specifies the target library.
        attysecg

        stb
        vrapi
        samplecommon
        sampleframework
        ${log-lib}
        nativewindow
        android
        GLESv3 EGL
        )
