/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.profilers;

import com.android.sdklib.AndroidVersion;
import com.android.tools.adtui.model.AspectModel;
import com.android.tools.profiler.proto.Common;
import org.jetbrains.annotations.NotNull;

/**
 * This stage gets set when the profilers first open, when selecting a device with no debuggable processes,
 * or when an unsupported device is selected. It represents the stage of no-process, no-device, and unsupported devices.
 */
public class NullMonitorStage extends Stage {

  private Type myType;

  private final AspectModel<Aspect> myAspect = new AspectModel<>();

  public NullMonitorStage(@NotNull StudioProfilers profiler) {
    super(profiler);
    getStudioProfilers().addDependency(this).onChange(ProfilerAspect.DEVICES, this::updateType);
    updateType();
  }

  public AspectModel<Aspect> getAspect() {
    return myAspect;
  }

  private void updateType() {
    Common.Device device = getStudioProfilers().getDevice();
    if (device == null) {
      myType = Type.NO_DEVICE;
    } else {
      try {
        int deviceFeatureLevel = device.getFeatureLevel();
        // Currently, we only support devices with API level 21 or higher
        if (deviceFeatureLevel < AndroidVersion.VersionCodes.LOLLIPOP) {
          myType = Type.UNSUPPORTED_DEVICE;
        } else {
          // If device is not null and has API level of 21 or higher,
          // we only create a NullMonitorStage if it doesn't have debuggable processes.
          myType = Type.NO_DEBUGGABLE_PROCESS;
        }
      } catch (NumberFormatException e) {
        // API level is unknown. We don't support such device.
        myType = Type.UNSUPPORTED_DEVICE;
      }
    }
    myAspect.changed(Aspect.NULL_MONITOR_TYPE);
  }

  public Type getType() {
    return myType;
  }

  @Override
  public void enter() {
    getStudioProfilers().getIdeServices().getFeatureTracker().trackEnterStage(getClass());
  }

  @Override
  public void exit() {
  }

  enum Type {
    // No device detected
    NO_DEVICE,
    // Selected device has no debuggable processes
    NO_DEBUGGABLE_PROCESS,
    // Selected device is unsupported
    UNSUPPORTED_DEVICE
  }

  enum Aspect {
    NULL_MONITOR_TYPE
  }
}
