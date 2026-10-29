-- English only. Prevent number selection and preserve raw punctuation/unknown words.
local function processor(key, env)
    local context = env.engine.context
    if context:get_option("ascii_mode") or key:release() or key:ctrl()
        or key:alt() or key:super() then
        return 2 -- kNoop
    end
    local code = key.keycode
    -- X11 keypad keysyms can arrive from hardware keyboards with Num Lock.
    if code >= 65456 and code <= 65465 then code = code - 65456 + 48 end
    if code == 65454 then code = 46 end
    if code >= 48 and code <= 57 and context:is_composing() then
        context:push_input(string.char(code))
        return 1 -- kAccepted; recognizer marks the whole alphanumeric input raw
    end
    if context:is_composing() then
        if code == 32 then
            if context:has_menu() then
                -- Confirm through Rime so the selected word is learned.
                context:confirm_current_selection()
                context:commit()
                env.engine:commit_text(" ")
            else
                env.engine:commit_text(context.input .. " ")
                context:clear()
            end
            return 1
        end
        -- Apostrophes belong to words. Other ASCII punctuation commits raw input,
        -- unless the recognizer can keep the address together on its own.
        if code > 32 and code < 127 and code ~= 39
            and not string.char(code):match("[%w]") then
            if code == 46 or code == 64 or code == 47 or code == 58 then
                context:push_input(string.char(code))
            else
                env.engine:commit_text(context.input .. string.char(code))
                context:clear()
            end
            return 1
        end
    end
    return 2
end

return processor
