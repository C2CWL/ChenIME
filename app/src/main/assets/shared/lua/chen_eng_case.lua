-- Shadow candidates retain their underlying dictionary entry for learning.
local function filter(input, env)
    local prefix = env.engine.context.input
    for candidate in input:iter() do
        local text = candidate.text
        if #prefix > 0 and text:sub(1, #prefix):lower() == prefix:lower() then
            local suffix = text:sub(#prefix + 1)
            if prefix:match("%u") and prefix == prefix:upper() then
                suffix = suffix:upper()
            end
            text = prefix .. suffix
        end
        if text ~= candidate.text then
            yield(ShadowCandidate(candidate, candidate.type, text, candidate.comment))
        else
            yield(candidate)
        end
    end
end

return filter
