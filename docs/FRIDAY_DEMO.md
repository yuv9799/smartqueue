# Friday progress demonstration

## What is honestly complete

The first working vertical slice is complete: customer check-in, service-time
prediction, min-heap counter assignment, staff actions, live statistics,
synthetic-data generation, model comparison, database design, and core tests.

This is not yet a production system. Authentication, PostgreSQL, real-store
data, notifications, and the baseline-versus-SmartQueue experiment belong to
the next milestones.

## Three-minute demonstration order

1. **Problem (25 seconds):** Manual counter selection creates uneven queues,
   uncertain waits, and weak evidence for opening additional counters.
2. **Solution (25 seconds):** SmartQueue predicts checkout time and assigns the
   customer to the open counter with the smallest predicted workload.
3. **Live workflow (75 seconds):** Load the demo queue, add one customer, show
   the token and assignment, call the next customer, and complete checkout.
4. **Engineering (35 seconds):** Show FastAPI docs, SQLite tables, the separated
   prediction and allocator modules, and the SRS diagrams.
5. **Evidence plan (20 seconds):** Explain that current data is labelled
   synthetic and that permission-based anonymous observations will replace it
   for final statistical testing.

## Short explanation

> Our project is SmartQueue, an ML-assisted checkout queue optimiser for retail
> stores. Instead of asking customers to guess the fastest physical line, the
> system estimates each checkout's service time from basket size, payment method,
> time of day, and day of week. It calculates the predicted workload of every
> open counter and uses a min-heap to assign the least-loaded counter. Staff can
> call and complete customers from a live dashboard, while the system records
> timings for later statistical evaluation. The current dataset is synthetic
> and clearly labelled; our next milestone is permission-based real observation
> and a controlled comparison against ordinary queue assignment.

## Likely questions

### Why is ML needed?

A queue with fewer people is not always faster: one large basket may take longer
than several small baskets. ML estimates service duration; DSA uses that
estimate to make an assignment.

### Why use a min-heap?

The counter with the smallest predicted workload must be selected repeatedly.
A min-heap is designed for fast access to the minimum. Heap construction is
`O(C)` and selection is `O(log C)` for `C` open counters.

### Is the current result scientifically proven?

No. The software pipeline works, but the present training data is synthetic.
The final claim requires real observations and a baseline-versus-proposed
experiment using the same arrival workload.

### Why SQLite instead of PostgreSQL?

SQLite makes the Friday demonstration portable and requires no database server.
The persistence layer will move to PostgreSQL for the final multi-user version.

### How does this reduce a physical queue?

It creates one digital arrival flow, balances work across counters, gives an
estimated wait, and lets management see congestion. It does not remove barcode
scanning or payment time; self-scanning would be a separate future feature.

### What is the startup opportunity?

The prototype establishes technical feasibility. Commercial viability still
requires real-store pilots, stakeholder interviews, operating-cost estimates,
and willingness-to-pay evidence.

