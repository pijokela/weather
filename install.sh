#!/bin/bash
sudo stop weather
./activator stage
sudo rm -rf /opt/weather-0.1
sudo cp -r target/universal/stage /opt/weather-0.1
sudo cp weather.conf /etc/init/
sudo cp $1 /opt/weather-0.1/conf/application.conf
sudo chown -R pi:users /opt/weather-0.1
sudo start weather
sudo tail -f /var/log/upstart/weather.log
