mambo-ec2-deploy
================

Mambo EC2 Deployment Package

This provides a set of packages and configuration files that help to launch a Hadoop cluster
on EC2. This is packaged with a CloudFormation script that launches the cluster.


Setting up VNC Server
---------------------

Install the required packages
> apt-get -y update
> apt-get -y install xfce4 emacs vnc4server firefox

Start xfce4 window manager
add "startxfce4 &" to xstartup

Tunnel VNC through SSH (-L5901:localhost:5901) and connect using VNC viewer

Go to http://10.0.0.10:8088/ to view Hadoop

