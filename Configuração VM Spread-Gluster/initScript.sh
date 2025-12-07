sudo rm -f /tmp/spreadlogs

sudo -u spread -g spread /usr/local/sbin/spread -c /usr/local/etc/vm35Spread.conf > /tmp/spreadlogs 2>&1 & 

sudo service glusterd start