#!/bin/sh

[ -z "$BASH_DEBUG" ] || set -xv

. /lib/lsb/init-functions

start() {
	local status=0
	start_daemon -p $PIDFILE $DAEMON || status="$?"
	case $status in
	  0) log_success_msg "$NAME: started (pid $(pidofproc -p $PIDFILE $DAEMON))" ;;
	  4) log_failure_msg "$NAME: insufficient privilege" ;;
	  *) log_failure_msg "$NAME: start: unexpected start_daemon status: $status" ;;
  esac
  return $status
}

stop() {
  local status=0
  killproc -p $PIDFILE $DAEMON || status="$?"
	case $status in
	  0) log_success_msg "$NAME: stopped" ;;
	  4) log_failure_msg "$NAME: insufficient privilege" ;;
	  *) log_failure_msg "$NAME: stop: unexpected killproc status: $status" ;;
  esac
  return $status
}

restart() {
  local status=0 pid
  pid="$(pidofproc -p $PIDFILE $DAEMON)" || status="$?"
	case $status in
	  0) stop; start ;;
	  1|3|4) start ;;
	  *) log_failure_msg "$NAME: restart: unexpected pidofproc status: $status"; return 1 ;;
  esac
}

tryrestart() {
  local status=0 pid
  pid="$(pidofproc -p $PIDFILE $DAEMON)" || status="$?"
	case $status in
	  0) restart ;;
	  1|3|4) log_success_msg "$NAME: not running"; return 0 ;;
	  *) log_failure_msg "$NAME: tryrestart: unexpected pidofproc status: $status"; return 1 ;;
  esac
}

reload() {
  local status=0
  killproc -p $PIDFILE $DAEMON -HUP || status="$?"
	case $status in
	  0) log_success_msg "$NAME: reloaded" ;;
	  4) log_failure_msg "$NAME: insufficient privilege" ;;
	  *) log_failure_msg "$NAME: reload: unexpected killproc status: $status" ;;
  esac
  return $status
}

status() {
  local status=0 pid
  pid="$(pidofproc -p $PIDFILE $DAEMON)" || status="$?"
	case $status in
	  0) log_success_msg "$NAME: running (pid $pid)" ;;
	  1) log_failure_msg "$NAME: dead but PIDFILE exists" ;;
	  3|4) log_failure_msg "$NAME: not running" ;;
	  *) log_failure_msg "$NAME: status: unexpected pidofproc status: $status" ;;
  esac
  return $status
}

##

if [ $# -ne 3 ]; then
  echo >&2 <<EOF
Usage: init-script <daemon> <script> <command>

       init-script /sbin/foo /etc/init.d/foo start

Use from /etc/init.d/myservice like:

       #!$0 /path/to/myservice/bin/myservice
       ### BEGIN INIT INFO
       ...
       ### END INIT INFO
EOF
  exit 2
fi

DAEMON="$1"
SCRIPT="$2"
shift 2

test -r "$SCRIPT" || {
  echo "init-script: ERROR: script not found: $SCRIPT" >&2
  exit 2
}

NAME="$(basename "$SCRIPT")"
export PIDFILE="$(dirname $(dirname "$DAEMON"))/log/$NAME.pid"

[ ! -r /etc/default/$NAME ] || . /etc/default/$NAME
[ ! -r /etc/sysconfig/$NAME ] || . /etc/sysconfig/$NAME

. $SCRIPT

case "$1" in
  start)
    start
    ;;
  stop)
    stop
    ;;
  restart)
    restart
    ;;
  tryrestart)
    tryrestart
    ;;
  reload|force-reload)
    reload
    ;;
  status)
    status
    ;;
  *)
    echo "Usage: $NAME {start|stop|restart|try-restart|reload|force-reload|status}" >&2
    exit 2
	;;
esac
