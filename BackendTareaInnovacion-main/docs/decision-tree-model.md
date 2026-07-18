# Decision Tree Credit Model

El sistema usa un Árbol de Decisión para clasificar perfiles crediticios y recomendar productos. No aprueba créditos automáticamente.

## Quick Path

1. Generar dataset y modelo: `python3 ml/train_decision_tree.py`.
2. Levantar backend local: `mvn spring-boot:run -Dspring-boot.run.profiles=local`.
3. Evaluar clientes desde el frontend; el backend lee `src/main/resources/ml/decision-tree-model.json`.

## Variables

| Variable | Uso | Estado |
|---|---|---|
| Ingresos mensuales | Capacidad económica base | Obligatoria |
| Historial de pagos | Porcentaje de pagos puntuales | Obligatoria |
| Nivel de ahorro | Respaldo financiero | Obligatoria |
| Créditos activos | Exposición crediticia | Obligatoria |
| Nivel de endeudamiento | Relación deuda/ingreso | Obligatoria |
| Capacidad de pago | Ingreso disponible estimado | Obligatoria |
| Antigüedad laboral | Estabilidad económica | Opcional en la app, estimada si falta |
| Score convencional | Señal tradicional de riesgo | Obligatoria |
| Productos previos | Relación bancaria histórica | Obligatoria |
| Mora financiera | Riesgo crítico | Obligatoria |
| Edad | Contexto demográfico | Opcional |

## Perfiles

| Perfil | Significado | Productos recomendados |
|---|---|---|
| Básico | Cliente con bajo historial, mora, baja capacidad o score débil | Cuenta de ahorro, microcrédito controlado, educación financiera |
| Intermedio | Cliente bancarizable con riesgo moderado y margen de mejora | Tarjeta de crédito, préstamo personal, consolidación de deuda |
| Avanzado | Cliente estable, buen score, ahorro y capacidad de pago | Crédito vehicular, crédito hipotecario, productos premium |

## Score Inteligente

El dataset entrena con un score de 0 a 100 basado en reglas realistas y luego el árbol aprende los cortes principales.

| Rango | Interpretación |
|---|---|
| 0-39 | Alto riesgo |
| 40-69 | Riesgo moderado |
| 70-100 | Perfil óptimo |

## Flujo ML

Cliente -> Registro -> Datos financieros -> Árbol de Decisión -> Score inteligente -> Clasificación -> Recomendación bancaria -> Dashboard.

## Dashboard Del Negocio

| Indicador | Visualización |
|---|---|
| Clientes registrados | Tarjeta |
| Clientes evaluados | Tarjeta |
| Score promedio | Tarjeta |
| Clientes Básicos/Intermedios/Avanzados | Tarjetas y dona |
| Productos más recomendados | Barras horizontales |
| Score promedio por segmento | Barras |
| Evolución de clientes captados | Línea |
| Clientes con alto riesgo | Tarjeta y barras |
| Clientes aptos por producto | Barras apiladas |
| Tasa de captación | Línea o gauge |

## Dashboard Técnico ML

| Métrica | Uso |
|---|---|
| Accuracy | Calidad general del modelo |
| Accuracy entrenamiento | Detectar sobreajuste |
| Accuracy prueba | Rendimiento esperado |
| Matriz de confusión | Errores entre Básico, Intermedio y Avanzado |
| Importancia de variables | Explicar decisiones del árbol |
| Visualización simplificada del árbol | Sustentar lógica en presentación |
| Registros entrenamiento/prueba | Trazabilidad académica |
