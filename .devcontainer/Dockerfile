# Devcontainer image for building the Android app.
FROM mcr.microsoft.com/devcontainers/java

USER root

# Android SDK locations
ENV ANDROID_PLATFORM=android-36
ENV ANDROID_BUILD_TOOLS=36.0.0
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_CMDLINE_TOOLS_ROOT=/opt/android-sdk/cmdline-tools/latest
ENV PATH=${PATH}:${ANDROID_CMDLINE_TOOLS_ROOT}/bin:${ANDROID_HOME}/platform-tools

# Remove broken/optional third-party apt sources that may be present in the base image/features
RUN rm -f /etc/apt/sources.list.d/yarn.list \
    /etc/apt/sources.list.d/yarn*.list \
    /etc/apt/sources.list.d/archive_uri-https_dl_yarnpkg_com_debian-bookworm.list || true

# Install packages required for Android/Gradle workflows
RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        curl \
        unzip \
        ca-certificates \
        git \
        bash \
        libc6 \
        libstdc++6 \
        libgcc1 \
        libbz2-1.0 \
        libncurses6 \
        zlib1g \
    # && uv tool install specify-cli --from git+https://github.com/github/spec-kit.git \
    && rm -rf /var/lib/apt/lists/*

# Install Android command line tools
RUN mkdir -p ${ANDROID_HOME}/cmdline-tools \
    && curl -fsSL https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -o /tmp/cmdline-tools.zip \
    && mkdir -p /tmp/android-cmdline-tools \
    && unzip -q /tmp/cmdline-tools.zip -d /tmp/android-cmdline-tools \
    && mv /tmp/android-cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest \
    && rm -rf /tmp/cmdline-tools.zip /tmp/android-cmdline-tools \
    && chown -R vscode:vscode ${ANDROID_HOME}

# Accept licenses and install commonly needed Android SDK components
RUN yes | sdkmanager --sdk_root=${ANDROID_HOME} --licenses > /dev/null || true \
    && sdkmanager --sdk_root=${ANDROID_HOME} \
        "platform-tools" \
        "build-tools;${ANDROID_BUILD_TOOLS}" \
        "platforms;${ANDROID_PLATFORM}"
