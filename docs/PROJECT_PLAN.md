# SmartQueue project plan

## Project objective

Test whether predicted service times and workload-aware counter assignment can
reduce checkout waiting and improve counter utilisation compared with ordinary
manual queue selection.

## Research hypothesis

- **H0:** SmartQueue does not reduce mean checkout waiting time compared with the baseline policy.
- **H1:** SmartQueue reduces mean checkout waiting time compared with the baseline policy.

Secondary measures are 95th-percentile wait, throughput, abandonment,
counter utilisation, prediction MAE, and the longest individual wait.

## Four connected workstreams

| Workstream | Deliverables | Friday state |
|---|---|---|
| Statistics | Variables, observation sheet, baseline metrics, hypothesis | Designed |
| Machine learning | Synthetic data generator, three-model comparison, saved predictor | Working prototype |
| DSA/business logic | Min-heap assignment, counter state rules, fairness constraint | Working prototype |
| Web interface | Customer check-in, staff controls, live management dashboard | Working prototype |

## Ethical data plan

1. Develop with clearly labelled synthetic data.
2. Request permission to observe a checkout or campus canteen.
3. Record operational timestamps and basket size only.
4. Do not record phone numbers, payment credentials, faces, or product choices.
5. Replace synthetic validation claims with real-data results only after collection.

## Suggested group ownership

Each member owns one workstream, but every member must be able to explain the
problem statement, system workflow, database, allocator, and evaluation result.
Code should be reviewed by at least one member other than its author.

## Roadmap

### Milestone 1 — Friday progress demonstration

- Runnable local MVP and API documentation.
- Queue check-in and min-heap assignment.
- Staff workflow and statistics dashboard.
- Synthetic data/model training pipeline.
- SRS and development roadmap.

### Milestone 2 — Real data and experiment

- Observation sheet and permission-based data collection.
- Baseline queue policy and simulation engine.
- Cross-validation, error analysis, and feature review.
- Comparison of baseline versus SmartQueue outcomes.

### Milestone 3 — Final product

- PostgreSQL migration and authentication.
- Store configuration and multiple locations.
- Optional QR check-in and customer status page.
- Statistical significance test and confidence interval.
- Automated tests, deployment, final report, and presentation.

## Definition of technical success

The prototype succeeds technically if it improves primary queue metrics on the
same arrival/service workload without creating unacceptable maximum waits.
Commercial viability would still require a real-store pilot, stakeholder
feedback, deployment cost estimates, and willingness-to-pay evidence.

