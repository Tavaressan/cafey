#pragma once

#include <ctime>

namespace cafey::core {

/**
 * @brief Fonte de tempo do sistema (spec §5.5 — disparo pelo relogio interno).
 *
 * Interface injetavel para permitir testes de host sem depender de tempo real:
 * o codigo de producao usa SystemClock; os testes injetam um relogio falso.
 */
class Clock {
public:
    virtual ~Clock() = default;

    /** Segundos desde a epoca Unix (UTC), como time(nullptr). */
    virtual std::time_t now() const = 0;
};

/** Relogio real: le o tempo mantido pelo sistema (sincronizado por NTP). */
class SystemClock : public Clock {
public:
    std::time_t now() const override { return std::time(nullptr); }
};

} // namespace cafey::core
