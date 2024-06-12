FROM python:3.11.3-alpine3.18

ADD requirements.txt .
RUN pip3 install -r requirements.txt

ADD crontab /home
ADD mqtt_fetch.py /home
ADD pgquery_rsu.py /home
ADD purger.py /home
ADD start.sh /home

# fix the line endings from windows
RUN dos2unix /home/crontab

RUN crontab /home

CMD ["sh", "/home/start.sh"]