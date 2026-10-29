#pragma once

#include <rime_api.h>

#ifdef __cplusplus
extern "C" {
#endif

size_t rime_get_highlighted_candidate_index(RimeSessionId session_id);

#ifdef __cplusplus
}
#endif

#ifdef __cplusplus
#include <vector>
std::vector<int> rime_get_candidate_ends(RimeSessionId session_id,
                                         int start_index, int limit);
#endif
