#pragma once

#include <rime/config.h>

namespace chen {

// Safe subset of rime-frost / rime-ice 「自动纠错」 derive rules: those
// upstream leaves enabled. Deploy-time only (~zero cost per key).
// The 简拼 side-effect group (x'ai/xia, hzi/zhi, etc.) stays disabled.
// Gated by 「拼音纠错」 through a `speller/algebra/+` append so existing
// algebra (简拼, ve/ue, upper-case derive) is not replaced.
inline constexpr const char* kAlgebraAppendKey = "speller/algebra/+";

inline constexpr const char* kTypoDeriveRules[] = {
    // ai: wia → wai
    "derive/^([wghk])ai$/$1ia/",
    // ei: wie → wei, fie → fei  (fiechang → feichang)
    "derive/([wfghkz])ei$/$1ie/",
    // ie: jei → jie
    "derive/([jqx])ie$/$1ei/",
    // ao
    "derive/([rtypsdghklzcbnm])ao$/$1oa/",
    // ou
    "derive/([ypfm])ou$/$1uo/",
    // ang
    "derive/([wrtypsdfghklzcbnm])ang$/$1nag/",
    "derive/([wrtypsdfghklzcbnm])ang$/$1agn/",
    // eng
    "derive/([wrtpsdfghklzcbnm])eng$/$1neg/",
    "derive/([wrtpsdfghklzcbnm])eng$/$1egn/",
    // ing
    "derive/([qtypdjlxbnm])ing$/$1nig/",
    "derive/([qtypdjlxbnm])ing$/$1ign/",
    // ong
    "derive/([rtysdghklzcn])ong$/$1nog/",
    "derive/([rtysdghklzcn])ong$/$1ogn/",
    // iao
    "derive/([qtpdjlxbnm])iao$/$1ioa/",
    "derive/([qtpdjlxbnm])iao$/$1oia/",
    // ui
    "derive/([rtsghkzc])ui$/$1iu/",
    // iu
    "derive/([qjlxnm])iu$/$1ui/",
    // iang
    "derive/([qjlxn])iang$/$1aing/",
    "derive/([qjlxn])iang$/$1inag/",
    // ua
    "derive/([g|k|h|zh|sh])ua$/$1au/",
    // uai
    "derive/([g|h|k|zh|ch|sh])uai$/$1aui/",
    // uan
    "derive/([qrtysdghjklzxcn])uan$/$1aun/",
    // ue
    "derive/([nlyjqx])ue$/$1eu/",
    // uang
    "derive/([g|h|k|zh|ch|sh])uang$/$1aung/",
    "derive/([g|h|k|zh|ch|sh])uang$/$1uagn/",
    "derive/([g|h|k|zh|ch|sh])uang$/$1unag/",
    "derive/([g|h|k|zh|ch|sh])uang$/$1augn/",
    // iong
    "derive/([jqx])iong$/$1inog/",
    "derive/([jqx])iong$/$1oing/",
    "derive/([jqx])iong$/$1iogn/",
    "derive/([jqx])iong$/$1oign/",
    // other upstream-enabled shortcuts
    "derive/([rtsdghkzc])o(u|ng)$/$1o/",
    "derive/(.+)ong$/$1on/",
    "derive/([tl])eng$/$1en/",
    "derive/([qwrtypsdfghjklzxcbnm])([aeio])ng$/$1ng/",
};

inline constexpr size_t kTypoDeriveRuleCount =
    sizeof(kTypoDeriveRules) / sizeof(kTypoDeriveRules[0]);

inline bool IsTypoDeriveRule(const rime::string& rule) {
  for (const char* expected : kTypoDeriveRules) {
    if (rule == expected) {
      return true;
    }
  }
  return false;
}

inline rime::an<rime::ConfigList> ExistingAlgebraAppend(
    const rime::an<rime::ConfigMap>& patch) {
  auto item = patch->Get(kAlgebraAppendKey);
  if (!item) {
    return rime::New<rime::ConfigList>();
  }
  if (auto list = rime::As<rime::ConfigList>(item)) {
    return list;
  }
  if (auto value = rime::As<rime::ConfigValue>(item)) {
    auto list = rime::New<rime::ConfigList>();
    list->Append(value);
    return list;
  }
  return nullptr;
}

inline bool ListHasRule(const rime::an<rime::ConfigList>& list,
                        const char* rule) {
  if (!list) {
    return false;
  }
  for (size_t i = 0; i < list->size(); ++i) {
    auto value = list->GetValueAt(i);
    rime::string text;
    if (value && value->GetString(&text) && text == rule) {
      return true;
    }
  }
  return false;
}

inline bool HasAllTypoDeriveRules(const rime::an<rime::ConfigMap>& patch) {
  auto list = rime::As<rime::ConfigList>(patch->Get(kAlgebraAppendKey));
  if (!list) {
    return false;
  }
  for (const char* rule : kTypoDeriveRules) {
    if (!ListHasRule(list, rule)) {
      return false;
    }
  }
  return true;
}

inline bool HasAnyTypoDeriveRule(const rime::an<rime::ConfigMap>& patch) {
  auto list = ExistingAlgebraAppend(patch);
  if (!list) {
    return false;
  }
  for (size_t i = 0; i < list->size(); ++i) {
    auto value = list->GetValueAt(i);
    rime::string text;
    if (value && value->GetString(&text) && IsTypoDeriveRule(text)) {
      return true;
    }
  }
  return false;
}

inline void AppendTypoDeriveRules(const rime::an<rime::ConfigMap>& patch) {
  auto list = ExistingAlgebraAppend(patch);
  if (!list) {
    return;
  }
  for (const char* rule : kTypoDeriveRules) {
    if (!ListHasRule(list, rule)) {
      list->Append(rime::New<rime::ConfigValue>(rule));
    }
  }
  patch->Set(kAlgebraAppendKey, list);
}

inline void RemoveTypoDeriveRules(const rime::an<rime::ConfigMap>& patch) {
  if (!patch->Get(kAlgebraAppendKey)) {
    return;
  }
  auto existing = ExistingAlgebraAppend(patch);
  if (!existing) {
    return;
  }
  auto kept = rime::New<rime::ConfigList>();
  for (size_t i = 0; i < existing->size(); ++i) {
    auto value = existing->GetValueAt(i);
    rime::string text;
    if (value && value->GetString(&text) && IsTypoDeriveRule(text)) {
      continue;
    }
    if (auto element = existing->GetAt(i)) {
      kept->Append(element);
    }
  }
  if (kept->empty()) {
    patch->Set(kAlgebraAppendKey, nullptr);
  } else {
    patch->Set(kAlgebraAppendKey, kept);
  }
}

// Changes only owned keys; unknown user patches survive serialization.
inline bool PatchPinyinCorrection(rime::Config& config, bool enabled) {
  auto patch = config.GetMap("patch");
  if (!patch) {
    patch = rime::New<rime::ConfigMap>();
  }
  auto value = patch->GetValue("translator/enable_correction");
  bool previous = false;
  const bool has_previous = value && value->GetBool(&previous);
  const bool algebra_ready =
      enabled ? HasAllTypoDeriveRules(patch) : !HasAnyTypoDeriveRule(patch);
  if (has_previous && previous == enabled && algebra_ready) {
    return false;
  }
  patch->Set("translator/enable_correction",
             rime::New<rime::ConfigValue>(enabled));
  if (enabled) {
    AppendTypoDeriveRules(patch);
  } else {
    RemoveTypoDeriveRules(patch);
  }
  config.SetItem("patch", patch);
  return true;
}

}  // namespace chen
