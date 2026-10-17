FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PIP_NO_CACHE_DIR=1

RUN groupadd --system officecare \
    && useradd --system --gid officecare --home-dir /app --shell /usr/sbin/nologin officecare

WORKDIR /app

COPY requirements.txt ./
RUN pip install --no-cache-dir --requirement requirements.txt

COPY --chown=officecare:officecare app ./app
COPY --chown=officecare:officecare scripts/run_prod.py ./scripts/run_prod.py
COPY --chown=officecare:officecare scripts/setup_agent_checkpoint.py ./scripts/setup_agent_checkpoint.py

USER officecare

EXPOSE 8000

CMD ["python", "scripts/run_prod.py"]
