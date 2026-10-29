#pragma once
#include <rime/config.h>

namespace chen {
// Changes only the owned patch key; unknown user patches survive serialization.
inline bool PatchPinyinCorrection(rime::Config& config, bool enabled) {
  auto patch = config.GetMap("patch");
  if (!patch) patch = rime::New<rime::ConfigMap>();
  auto value = patch->GetValue("translator/enable_correction");
  bool previous;
  if (value && value->GetBool(&previous) && previous == enabled) return false;
  patch->Set("translator/enable_correction",
             rime::New<rime::ConfigValue>(enabled));
  config.SetItem("patch", patch);
  return true;
}
}  // namespace chen
