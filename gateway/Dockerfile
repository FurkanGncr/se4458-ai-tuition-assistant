# syntax=docker/dockerfile:1
# Kong gateway image with the declarative config baked in, so the same image runs in
# docker compose and in the cloud (where no local folder can be mounted).
# Build: docker compose build kong

FROM kong:3.9.1

# The first COPY creates /kong with the same mode (755), so it must not be the 644 file.
COPY --chmod=755 render-config.sh /kong/render-config.sh
COPY --chmod=644 kong.yml /kong/kong.yml

# render-config.sh fills ${JWT_SECRET} and ${TUITION_API_URL} from the environment, then starts Kong.
ENTRYPOINT ["/kong/render-config.sh"]
