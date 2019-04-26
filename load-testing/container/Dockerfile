FROM python:3.7-stretch

WORKDIR /usr/src/app

ENV LC_ALL=C.UTF-8
ENV LANG=C.UTF-8

COPY tools/ ./

RUN pip install pipenv && \
    pipenv install --system

ENTRYPOINT [ "python", "./start_test.py" ]
