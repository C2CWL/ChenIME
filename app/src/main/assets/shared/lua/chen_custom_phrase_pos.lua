-- Candidate positions from stabledb commits; no file IO or cross-query state.
local M = {}

function M.init(env)
    env.dictionary = env.engine.schema.config:get_string("custom_phrase/user_dict") or "custom_phrase"
end

-- Pure streaming merge, also used by the standalone tests. Only the high-quality
-- custom prefix is retained; normal candidates are emitted one at a time.
function M.reorder(next_candidate, position, emit)
    local pending, count = {}, 0
    local cand = next_candidate()
    -- pin_cand_filter deliberately precedes us. Pinned slots stay occupied.
    while cand and cand.type == "chen_pinned" do
        emit(cand)
        count = count + 1
        cand = next_candidate()
    end
    while cand do
        local n = position(cand)
        if not n then break end
        pending[#pending + 1] = {cand = cand, n = n, order = #pending + 1}
        cand = next_candidate()
    end
    table.sort(pending, function(a, b)
        if a.n == b.n then return a.order < b.order end
        return a.n < b.n
    end)
    local i = 1
    while cand do
        while pending[i] and pending[i].n <= count + 1 do
            emit(pending[i].cand)
            count, i = count + 1, i + 1
        end
        emit(cand)
        count = count + 1
        cand = next_candidate()
    end
    -- A requested slot beyond the available normal candidates goes last.
    while pending[i] do
        emit(pending[i].cand)
        i = i + 1
    end
end

function M.func(input, env)
    local context = env.engine.context
    local segment = context.composition:back()
    local raw = context.input
    local function position(cand)
        local genuine = cand:get_genuine()
        if genuine.type ~= "user_table" then return nil end
        local phrase = genuine:to_phrase()
        if not phrase or phrase.lang_name ~= env.dictionary then return nil end
        -- custom_code includes stabledb's trailing key separator space.
        if not segment or cand.start ~= segment.start or cand._end ~= segment._end
            or cand._end ~= #raw then return nil end
        local code = phrase.entry.custom_code:gsub(" +$", "")
        if code ~= raw:sub(segment.start + 1, segment._end) then return nil end
        return math.max(1, phrase.entry.commit_count)
    end
    local next_candidate, state = input:iter()
    M.reorder(function() return next_candidate(state) end, position, yield)
end

-- No notifier connections or mutable query state to release.
function M.fini(env)
    env.dictionary = nil
end

return M
