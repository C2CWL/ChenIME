-- Keep the first original candidate ahead of native keyboard corrections.
-- Native translation limits corrections; normal streams remain fully lazy.
local M = {}

function M.func(input)
    local pending = {}
    for cand in input:iter() do
        if pending then
            if cand.type == "chen_correction" then
                pending[#pending + 1] = cand
            else
                yield(cand)
                for _, correction in ipairs(pending) do yield(correction) end
                pending = nil
            end
        else
            yield(cand)
        end
    end
    if pending then
        for _, correction in ipairs(pending) do yield(correction) end
    end
end

return M
