FROM ubuntu:24.04
ARG AUDIVERIS_VERSION=5.11.0
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates curl python3 xvfb tesseract-ocr tesseract-ocr-eng libglib2.0-0 libgl1 \
    && rm -rf /var/lib/apt/lists/*
# The official Audiveris .deb includes a bundled JRE.
RUN mkdir -p /usr/share/desktop-directories /usr/share/mime/packages \
    /usr/share/icons/hicolor /usr/share/applications \
    && curl -fsSL --retry 3 -o /tmp/audiveris.deb \
      "https://github.com/Audiveris/audiveris/releases/download/${AUDIVERIS_VERSION}/Audiveris-${AUDIVERIS_VERSION}-ubuntu24.04-x86_64.deb" \
    && apt-get update && apt-get install -y --no-install-recommends /tmp/audiveris.deb \
    && rm /tmp/audiveris.deb && rm -rf /var/lib/apt/lists/* \
    && test -x /opt/audiveris/bin/Audiveris
RUN useradd -m -u 10001 piano
RUN printf '#!/bin/sh\nexec xvfb-run -a /opt/audiveris/bin/Audiveris "$@"\n' \
    > /usr/local/bin/audiveris-headless && chmod 755 /usr/local/bin/audiveris-headless
WORKDIR /app
COPY server.py /app/server.py
USER piano
ENV HOME=/home/piano OMR_LISTEN=0.0.0.0 OMR_PORT=8080 \
    AUDIVERIS_BIN=/usr/local/bin/audiveris-headless
EXPOSE 8080
CMD ["python3", "/app/server.py"]
