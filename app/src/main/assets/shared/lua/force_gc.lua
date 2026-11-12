-- 轻量 GC。
-- 详情 https://github.com/hchunhui/librime-lua/issues/307
-- collectgarbage()：默认调用，等同于 collectgarbage("collect")，触发完整的垃圾回收。
-- collectgarbage("step")：执行垃圾回收的一小步。
-- 每个按键都 step 会把几毫秒加进 rime engine 时间；每隔 N 次查询步进一次，内存同样稳定。
local STEP_EVERY = 16
local n = 0
local function force_gc()
    -- collectgarbage()
    n = n + 1
    if n % STEP_EVERY == 0 then
        collectgarbage("step")
    end
end

return force_gc
