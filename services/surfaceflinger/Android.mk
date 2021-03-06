LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
    Layer.cpp 								\
    LayerBase.cpp 							\
    LayerDim.cpp 							\
    LayerScreenshot.cpp						\
    DdmConnection.cpp						\
    DisplayHardware/DisplayHardware.cpp 	\
    DisplayHardware/DisplayHardwareBase.cpp \
    DisplayHardware/HWComposer.cpp 			\
    GLExtensions.cpp 						\
    MessageQueue.cpp 						\
    SurfaceFlinger.cpp 						\
    SurfaceTextureLayer.cpp 				\
    Transform.cpp 							\
    

LOCAL_CFLAGS:= -DLOG_TAG=\"SurfaceFlinger\"
LOCAL_CFLAGS += -DGL_GLEXT_PROTOTYPES -DEGL_EGLEXT_PROTOTYPES

ifeq ($(BOARD_NO_RGBX_8888),true)
  LOCAL_CFLAGS += -DNO_RGBX_8888
endif

ifeq ($(TARGET_BOARD_PLATFORM), omap3)
	LOCAL_CFLAGS += -DNO_RGBX_8888
endif
ifeq ($(TARGET_BOARD_PLATFORM), omap4)
	LOCAL_CFLAGS += -DHAS_CONTEXT_PRIORITY
endif
ifeq ($(TARGET_BOARD_PLATFORM), s5pc110)
	LOCAL_CFLAGS += -DHAS_CONTEXT_PRIORITY -DNEVER_DEFAULT_TO_ASYNC_MODE -DSCREEN_RELEASE
	LOCAL_CFLAGS += -DREFRESH_RATE=56
endif
ifneq ($(BOARD_OVERRIDE_FB0_WIDTH),)
	LOCAL_CFLAGS += -DOVERRIDE_FB0_WIDTH=$(BOARD_OVERRIDE_FB0_WIDTH)
endif
ifneq ($(BOARD_OVERRIDE_FB0_HEIGHT),)
	LOCAL_CFLAGS += -DOVERRIDE_FB0_HEIGHT=$(BOARD_OVERRIDE_FB0_HEIGHT)
endif

ifeq ($(BOARD_USES_QCOM_HARDWARE),true)
LOCAL_SHARED_LIBRARIES := \
	libQcomUI
LOCAL_C_INCLUDES += hardware/qcom/display/libqcomui
LOCAL_CFLAGS += -DQCOM_HARDWARE
endif

LOCAL_SHARED_LIBRARIES := \
	libcutils \
	libhardware \
	libutils \
	libEGL \
	libGLESv1_CM \
	libbinder \
	libui \
	libgui

# this is only needed for DDMS debugging
LOCAL_SHARED_LIBRARIES += libdvm libandroid_runtime

ifeq ($(BOARD_USES_LGE_HDMI_ROTATION),true)
LOCAL_CFLAGS += -DUSE_LGE_HDMI
LOCAL_SHARED_LIBRARIES += \
	libnvdispmgr_d
endif

ifeq ($(BOARD_ADRENO_DECIDE_TEXTURE_TARGET),true)
    LOCAL_CFLAGS += -DDECIDE_TEXTURE_TARGET
endif

LOCAL_C_INCLUDES := \
	$(call include-path-for, corecg graphics)

LOCAL_C_INCLUDES += hardware/libhardware/modules/gralloc

ifeq ($(BOARD_HAVE_CODEC_SUPPORT),SAMSUNG_CODEC_SUPPORT)
LOCAL_CFLAGS += -DSAMSUNG_CODEC_SUPPORT
endif

ifeq ($(BOARD_HAVE_HDMI_SUPPORT),SAMSUNG_HDMI_SUPPORT)
LOCAL_CFLAGS += -DSAMSUNG_HDMI_SUPPORT
LOCAL_SHARED_LIBRARIES += libhdmiclient libTVOut
LOCAL_C_INCLUDES += $(TARGET_HAL_PATH)/libhdmi/libhdmiservice
LOCAL_C_INCLUDES += $(TARGET_HAL_PATH)/include
endif

ifeq ($(BOARD_USES_QCOM_HARDWARE),true)
ifeq ($(TARGET_HAVE_BYPASS),true)
    LOCAL_CFLAGS += -DBUFFER_COUNT_SERVER=3
else
    LOCAL_CFLAGS += -DBUFFER_COUNT_SERVER=2
endif

LOCAL_SHARED_LIBRARIES += \
	libQcomUI
LOCAL_C_INCLUDES += hardware/qcom/display/libqcomui
ifeq ($(TARGET_QCOM_HDMI_OUT),true)
LOCAL_CFLAGS += -DQCOM_HDMI_OUT
endif
endif

LOCAL_MODULE:= libsurfaceflinger

include $(BUILD_SHARED_LIBRARY)
