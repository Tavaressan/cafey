#include "reconnect_policy.hpp"

#include <algorithm>

namespace cafey::core {

uint32_t ReconnectPolicy::next_delay_ms() {
    uint32_t delay = current_delay_ms_;
    attempts_++;
    current_delay_ms_ = std::min<uint32_t>(current_delay_ms_ * 2, kMaxDelayMs);
    return delay;
}

void ReconnectPolicy::reset() {
    attempts_ = 0;
    current_delay_ms_ = kBaseDelayMs;
}

uint32_t ReconnectPolicy::attempts() const noexcept {
    return attempts_;
}

} // namespace cafey::core
