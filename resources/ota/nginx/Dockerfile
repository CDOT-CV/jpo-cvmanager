FROM nginx:1.30.7-alpine3.24

RUN apk update && apk add \
    apache2-utils \
    jq \
    openssl \
    bash

CMD ["nginx", "-g", "daemon off;"]