#!/bin/bash
./activator stage
sudo stop weather
sudo rm -rf /opt/weather-0.1
sudo cp -r target/universal/stage /opt/weather-0.1
sudo start weather
sudo tail -f /var/log/upstart/weather.log