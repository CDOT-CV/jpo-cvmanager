FROM python:3-slim

WORKDIR /home

ADD requirements.txt .

RUN pip3 install -r requirements.txt

ADD psm_query.py .

CMD ["/home/psm_query.py"]
ENTRYPOINT ["python3"]