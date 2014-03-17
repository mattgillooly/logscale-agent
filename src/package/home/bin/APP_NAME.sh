#!/bin/bash

[ -z "$BASH_DEBUG" ] || set -xv

cd $(dirname $(dirname $0))

JAVA=${template_app_jre_dir_name}/bin/java
JAVA_ARGS="-Djava.io.tmpdir=tmp -cp 'lib/*:lib/ext/*' ${template_app_main_class}"
ARGS="file:conf.yml"

[ -n "$OUTFILE" ] || OUTFILE=log/${template_app_name}.out
[ -n "$PIDFILE" ] || PIDFILE=log/${template_app_name}.pid

CMD="exec $JAVA $JAVA_ARGS $ARGS >>$OUTFILE 2>&1 </dev/null"

if [ -n "$DAEMON_USER" ]; then
  if [ -x /sbin/runuser ]; then
    CMD="runuser -s /bin/bash $DAEMON_USER -c \"$CMD\""
  else
    CMD="su -s /bin/bash $DAEMON_USER -c \"$CMD\""
  fi
fi

cat >$OUTFILE <<EOF
## ${template_app_name}
# date: $(date)
# cmd: "$CMD"
# pwd: $(pwd)
# env:
$(env)
##

EOF

if [ -n "$DAEMON_USER" ]; then
  chown $DAEMON_USER:$DAEMON_USER $OUTFILE
fi

rm -f "$PIDFILE"

eval $CMD &

timeout=30
while [ ! -f "$PIDFILE" ] && [ $timeout -gt 0 ]; do
  ((timeout--))
  sleep 1
done

if [ ! -f "$PIDFILE" ]; then
  echo "timeout waiting for PIDFILE: $PIDFILE" >&2
  exit 1
fi
