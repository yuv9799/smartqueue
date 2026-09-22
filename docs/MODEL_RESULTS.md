# Model comparison

These results use **synthetic simulation data**, not observations from a real store.
They validate the pipeline only; real-world claims require real checkout observations.

| Model | MAE (seconds) | RMSE (seconds) | R² |
|---|---:|---:|---:|
| Random Forest | 15.74 | 19.87 | 0.828 |
| Linear Regression | 16.08 | 20.19 | 0.823 |
| Decision Tree | 17.98 | 22.95 | 0.771 |

Selected model: **Random Forest**, using lowest validation MAE.
