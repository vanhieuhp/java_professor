# Distributed Counter Lab

This project is a step-by-step lab for understanding why a counter becomes hard in a distributed system.

## Lab 1: local concurrency

Goal: prove that `value = value + 1` is not atomic once many threads hit the same counter.

Run:

```powershell
.\gradlew.bat test --tests hieunv.dev.netflixstack.counter.CounterConcurrencyTest
```

The lab compares four strategies:

| Strategy | Correct under concurrency? | Main lesson |
| --- | --- | --- |
| `Counter` using plain `long` | No | Read-modify-write loses updates. |
| `SynchronizedCounter` | Yes | A critical section protects correctness but serializes access. |
| `AtomicLongCounter` | Yes | CAS gives lock-free atomic increments. |
| `LongAdderCounter` | Yes | Contention is spread across cells and summed on read. |

This is the local version of the larger Netflix-style counter problem: a single hot value becomes a bottleneck before Kafka, Redis, PostgreSQL, sharding, or multi-region design even enter the picture.
