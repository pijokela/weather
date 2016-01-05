An app for storing and viewing weather data
===========================================

Right now this application can read one-wire data and store it in postgresql.
I have setup a Raspberrypi 2 to measure temperatures and store the data.

Installation
============

sudo apt-get install wget
sudo apt-get install postgresql

This will replace sysvinit so there is a huge warning message.
It all went fine on my pi, so just accept it 
(and reflash the memory card if it stops working).

sudo apt-get install upstart

sudo -u postgres psql postgres < db/create-db.sql

sudo -u postgres psql postgres

Set password with:
\password postgres

The password in application.conf is postgres.

In the weather directory:
./activator stage
sudo cp -r target/universal/stage /opt/weather-0.1
sudo chown -R pi:users /opt/weather-0.1
sudo cp weather.conf /etc/init/

sudo start weather

See that is starts working:
sudo tail -f /var/log/upstart/weather.log

Crontab timed measuring
=======================
crontab -e

insert a row that wgets localhost:9000/measure:
*/10 * * * *   wget -O- http://localhost:9000/measure >/dev/null

CURL Examples
=============

Store a single data point:

´´´
curl --verbose -XPOST 'http://localhost:9000/data' -H 'ContentType: application/json' --data '{
"data": [
  {"date": "", "deviceId": "test1", "milliC": 20000}
]}'
´´´

´´´
curl -H "Accept: application/json" -H "Content-type: application/json" \
	-X POST -d '{"data":[{
	"date": "2015-01-01T10:11:12+02:00", "deviceId": "test1", "milliC": 20000
	}]}' http://localhost:9000/data
´´´
