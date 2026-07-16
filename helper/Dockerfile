# ChaosInjector helper image.
#
# Injectors run this image against the target (network faults join the target's
# network namespace; it also carries stress-ng for CPU/RAM fallbacks). Keep it
# tiny. Build and push once, then reference it via chaosinjector.helper.image
# (default: chaosinjector/helper:latest).
#
#   docker build -t chaosinjector/helper:latest helper/
#
FROM alpine:3.20

# iproute2 -> tc/netem for network faults; stress-ng -> CPU/memory load
RUN apk add --no-cache iproute2 stress-ng

# Injectors invoke `sh -c "<command>"`; no fixed entrypoint needed.
CMD ["sh"]
