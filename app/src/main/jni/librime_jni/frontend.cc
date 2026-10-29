#include "frontend.h"

#include <rime/context.h>
#include <rime/menu.h>
#include <rime/service.h>

using namespace rime;

size_t rime_get_highlighted_candidate_index(RimeSessionId session_id) {
  an<Session> session(Service::instance().GetSession(session_id));
  if (!session) return 0;
  Context* ctx = session->context();
  if (!ctx || !ctx->HasMenu()) return 0;
  auto& seg(ctx->composition().back());
  return seg.selected_index;
}

std::vector<int> rime_get_candidate_ends(RimeSessionId session_id,
                                         int start_index, int limit) {
  an<Session> session(Service::instance().GetSession(session_id));
  if (!session || start_index < 0 || limit <= 0) return {};
  Context* ctx = session->context();
  if (!ctx || !ctx->HasMenu() || ctx->composition().empty()) return {};
  auto& seg = ctx->composition().back();
  auto menu = seg.menu;
  if (!menu) return {};
  menu->Prepare(static_cast<size_t>(start_index) + limit);
  std::vector<int> result{static_cast<int>(seg.start),
                          static_cast<int>(seg.end)};
  for (size_t i = start_index; i < menu->candidate_count() &&
                               i < static_cast<size_t>(start_index) + limit;
       ++i) {
    result.push_back(static_cast<int>(menu->GetCandidateAt(i)->end()));
  }
  return result;
}
