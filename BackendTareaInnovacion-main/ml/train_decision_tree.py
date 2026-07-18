#!/usr/bin/env python3
"""Generate a synthetic credit dataset and train a small CART decision tree.

The script intentionally uses only the Python standard library so the project can
be reproduced on machines without scikit-learn installed.
"""

from __future__ import annotations

import csv
import json
import math
import random
from collections import Counter
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DATASET_PATH = ROOT / "src/main/resources/ml/credit_decision_dataset.csv"
MODEL_PATH = ROOT / "src/main/resources/ml/decision-tree-model.json"
METRICS_PATH = ROOT / "src/main/resources/ml/decision-tree-metrics.json"

RANDOM_SEED = 42
N_ROWS = 800
MAX_DEPTH = 5
MIN_SAMPLES_SPLIT = 30
CLASSES = ["BASIC", "INTERMEDIATE", "ADVANCED"]

FEATURES = [
    "monthly_income",
    "payment_history_rate",
    "savings_level",
    "active_credits",
    "debt_ratio",
    "payment_capacity",
    "employment_months",
    "conventional_score",
    "product_count",
    "has_mora",
    "age",
]


@dataclass
class Sample:
    features: dict[str, float]
    label: str
    intelligent_score: int
    recommended_products: str


def clamp(value: float, low: float, high: float) -> float:
    return max(low, min(high, value))


def weighted_choice(items: list[tuple[float, tuple[int, int]]]) -> int:
    total = sum(weight for weight, _ in items)
    pick = random.uniform(0, total)
    current = 0.0
    for weight, (low, high) in items:
        current += weight
        if pick <= current:
            return random.randint(low, high)
    low, high = items[-1][1]
    return random.randint(low, high)


def generate_sample() -> Sample:
    age = weighted_choice([(0.18, (18, 25)), (0.52, (26, 45)), (0.22, (46, 60)), (0.08, (61, 70))])
    monthly_income = round(random.triangular(950, 18000, 4200), 2)
    employment_months = int(clamp((age - 18) * random.uniform(3, 12) + random.gauss(18, 12), 0, 360))
    savings_months = clamp(random.gauss(2.2, 2.0), 0, 14)
    savings_level = round(monthly_income * savings_months * random.uniform(0.45, 1.25), 2)

    active_credits = weighted_choice([(0.30, (0, 0)), (0.36, (1, 1)), (0.22, (2, 3)), (0.12, (4, 6))])
    debt_ratio = clamp(random.betavariate(2.0, 4.2) + (active_credits * 0.045), 0, 1.15)
    current_debt_payment = monthly_income * debt_ratio
    payment_capacity = round(clamp(((monthly_income - current_debt_payment) / monthly_income) * 100, 0, 100), 2)

    has_mora_probability = clamp(0.08 + debt_ratio * 0.38 + max(0, active_credits - 3) * 0.07, 0.03, 0.70)
    has_mora = 1 if random.random() < has_mora_probability else 0
    payment_history_rate = clamp(random.gauss(0.90 - has_mora * 0.23 - debt_ratio * 0.16, 0.08), 0.25, 1.0)
    product_count = int(clamp(active_credits + random.choice([0, 1, 1, 2, 3]), 0, 8))

    conventional_score = int(clamp(
        35
        + min(monthly_income / 18000 * 14, 14)
        + min(savings_months * 3.0, 18)
        + payment_history_rate * 22
        + (payment_capacity / 100) * 16
        + min(employment_months / 120 * 8, 8)
        - has_mora * 20
        - max(0, active_credits - 3) * 4,
        0,
        100,
    ))

    intelligent_score = int(clamp(
        conventional_score * 0.55
        + payment_history_rate * 22
        + (payment_capacity / 100) * 16
        + min(savings_months * 2.0, 7)
        - has_mora * 16
        - (10 if debt_ratio > 0.65 else 0),
        0,
        100,
    ))

    if has_mora or intelligent_score < 40 or payment_capacity < 35:
        label = "BASIC"
        product_options = ["Cuenta de ahorro", "Microcredito controlado", "Programa de educacion financiera", "Debito automatico", "Ahorro programado"]
    elif intelligent_score < 70 or active_credits >= 4:
        label = "INTERMEDIATE"
        product_options = ["Tarjeta de credito inicial", "Prestamo personal", "Consolidacion de deuda", "Credito de consumo", "Seguro de proteccion de pagos"]
    else:
        label = "ADVANCED"
        product_options = ["Credito vehicular", "Credito hipotecario", "Productos premium", "Linea paralela", "Cuenta sueldo premium", "Fondos mutuos"]

    product_count = 2 if label == "BASIC" else random.choice([2, 3, 3, 4])
    products = "; ".join(random.sample(product_options, product_count))

    return Sample(
        features={
            "monthly_income": monthly_income,
            "payment_history_rate": round(payment_history_rate * 100, 2),
            "savings_level": savings_level,
            "active_credits": active_credits,
            "debt_ratio": round(debt_ratio * 100, 2),
            "payment_capacity": payment_capacity,
            "employment_months": employment_months,
            "conventional_score": conventional_score,
            "product_count": product_count,
            "has_mora": has_mora,
            "age": age,
        },
        label=label,
        intelligent_score=intelligent_score,
        recommended_products=products,
    )


def gini(labels: list[str]) -> float:
    total = len(labels)
    counts = Counter(labels)
    return 1.0 - sum((count / total) ** 2 for count in counts.values())


def majority(labels: list[str]) -> str:
    return Counter(labels).most_common(1)[0][0]


def best_split(rows: list[Sample]) -> tuple[str | None, float | None, float]:
    parent_gini = gini([row.label for row in rows])
    best_feature = None
    best_threshold = None
    best_gain = 0.0

    for feature in FEATURES:
        values = sorted({row.features[feature] for row in rows})
        if len(values) < 2:
            continue
        thresholds = [(values[i] + values[i + 1]) / 2 for i in range(len(values) - 1)]
        if len(thresholds) > 32:
            step = math.ceil(len(thresholds) / 32)
            thresholds = thresholds[::step]

        for threshold in thresholds:
            left = [row for row in rows if row.features[feature] <= threshold]
            right = [row for row in rows if row.features[feature] > threshold]
            if not left or not right:
                continue
            weighted = (len(left) / len(rows)) * gini([row.label for row in left]) + (len(right) / len(rows)) * gini([row.label for row in right])
            gain = parent_gini - weighted
            if gain > best_gain:
                best_feature = feature
                best_threshold = threshold
                best_gain = gain

    return best_feature, best_threshold, best_gain


def build_tree(rows: list[Sample], depth: int = 0) -> dict:
    labels = [row.label for row in rows]
    prediction = majority(labels)
    counts = dict(Counter(labels))

    if depth >= MAX_DEPTH or len(rows) < MIN_SAMPLES_SPLIT or len(set(labels)) == 1:
        return {"prediction": prediction, "samples": len(rows), "classCounts": counts}

    feature, threshold, gain = best_split(rows)
    if feature is None or threshold is None or gain <= 0.001:
        return {"prediction": prediction, "samples": len(rows), "classCounts": counts}

    left_rows = [row for row in rows if row.features[feature] <= threshold]
    right_rows = [row for row in rows if row.features[feature] > threshold]
    return {
        "feature": feature,
        "threshold": round(threshold, 4),
        "samples": len(rows),
        "classCounts": counts,
        "left": build_tree(left_rows, depth + 1),
        "right": build_tree(right_rows, depth + 1),
    }


def predict(tree: dict, features: dict[str, float]) -> str:
    node = tree
    while "prediction" not in node:
        node = node["left"] if features[node["feature"]] <= node["threshold"] else node["right"]
    return node["prediction"]


def tree_importances(tree: dict, totals: Counter[str] | None = None) -> dict[str, int]:
    totals = totals or Counter()
    if "prediction" in tree:
        return dict(totals)
    totals[tree["feature"]] += tree["samples"]
    tree_importances(tree["left"], totals)
    tree_importances(tree["right"], totals)
    return dict(totals)


def write_dataset(rows: list[Sample]) -> None:
    DATASET_PATH.parent.mkdir(parents=True, exist_ok=True)
    with DATASET_PATH.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=FEATURES + ["intelligent_score", "profile", "recommended_products"])
        writer.writeheader()
        for row in rows:
            writer.writerow({**row.features, "intelligent_score": row.intelligent_score, "profile": row.label, "recommended_products": row.recommended_products})


def main() -> None:
    random.seed(RANDOM_SEED)
    rows = [generate_sample() for _ in range(N_ROWS)]
    random.shuffle(rows)
    split_at = int(len(rows) * 0.8)
    train_rows = rows[:split_at]
    test_rows = rows[split_at:]

    tree = build_tree(train_rows)
    train_predictions = [predict(tree, row.features) for row in train_rows]
    test_predictions = [predict(tree, row.features) for row in test_rows]

    train_accuracy = sum(pred == row.label for pred, row in zip(train_predictions, train_rows)) / len(train_rows)
    test_accuracy = sum(pred == row.label for pred, row in zip(test_predictions, test_rows)) / len(test_rows)

    confusion = {actual: {predicted: 0 for predicted in CLASSES} for actual in CLASSES}
    for prediction, row in zip(test_predictions, test_rows):
        confusion[row.label][prediction] += 1

    raw_importances = tree_importances(tree)
    total_importance = sum(raw_importances.values()) or 1
    importances = {feature: round(raw_importances.get(feature, 0) / total_importance, 4) for feature in FEATURES}

    model = {
        "algorithm": "Decision Tree CART",
        "randomSeed": RANDOM_SEED,
        "classes": CLASSES,
        "features": FEATURES,
        "tree": tree,
        "profileProducts": {
            "BASIC": ["Cuenta de ahorro", "Microcredito controlado", "Educacion financiera"],
            "INTERMEDIATE": ["Tarjeta de credito", "Prestamo personal", "Consolidacion de deuda"],
            "ADVANCED": ["Credito vehicular", "Credito hipotecario", "Productos premium"],
        },
    }
    metrics = {
        "records": len(rows),
        "trainingRecords": len(train_rows),
        "testRecords": len(test_rows),
        "accuracy": round(test_accuracy, 4),
        "trainingAccuracy": round(train_accuracy, 4),
        "testAccuracy": round(test_accuracy, 4),
        "confusionMatrix": confusion,
        "featureImportance": importances,
    }

    write_dataset(rows)
    MODEL_PATH.write_text(json.dumps(model, indent=2, ensure_ascii=False), encoding="utf-8")
    METRICS_PATH.write_text(json.dumps(metrics, indent=2, ensure_ascii=False), encoding="utf-8")
    print(json.dumps(metrics, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
