# cadc-access-control-admin

This module provides a command line tool for managing users. It uses the persistence layer code (rather than the web
service) for the various functions.

## configuration

See the [cadc-java](https://github.com/opencadc/docker-base/tree/master/cadc-java)
image docs for general config requirements.

Runtime configuration must be made available via the `/config` directory.

### ac-admin-email.properties
This file is used by the cadc-access-control-admin tool for sending
email messages for account approval to newly approved users, and
mass emails to all users.

If this file is not present the admin tool will continue to function
but will be unable to send email messages.
```
# required fields for all messages:
#
#  SMTP host for bulk email
#    smtp.host=<host>                   The SMTP host name.
#    smtp.port=<port>                   The SMTP host port number.
#
#  SMTP host for account approval
#    smtp.auth.host=<host>              The SMTP auth host name.
#    smtp.auth.port=<port>              The SMTP auth host port number.
#    smtp.auth.account=<account>        The SMTP host account name.
#    smtp.auth.password=<password>      The SMTP host password.
#
# required fields for account approval messages:
#    mail.from=<email addr>             The from address.
#    mail.reply-to=<reply to addr>      The reply to address.
#    mail.subject                       The subject of the email.
#    mail.body=body                     The email body. The %s character in the
#                                       body will be replaced with the user's
#                                       userid (if present).
#
# optional field for account approval messages:
#
#    mail.bcc=<bcc addr>               A single bcc email address

smtp.host=example.host
smtp.port=25
smtp.auth.host=example-auth.host
smtp.auth.port=587
smtp.auth.account=account@example.host
smtp.auth.password=changeme

mail.from=id@example.com
mail.reply-to=id@example.com
mail.subject=New Account
mail.body=<p>Dear User</p> \
          <p>Your new account is %s </p>\
          <p>Thank you</p>

mail.bcc=id@example.com
```

### send-email.properties
This file... TODO.
```
mail.from=id@example.com
mail.to=id@example.com
mail.reply-to=id@example.com
mail.subject=Email Subject
mail.body=<p>Body of the email</p>
mail.skip-domains=<domain> <domain> Space separated domains. Addresses
                                    ending in these domains will not
                                    be added to the BBC list.
```

## building it
```
gradle clean build
docker build -t cadc-access-control-admin -f Dockerfile .
```

## checking it
```
docker run -it cadc-access-control-admin:latest /bin/bash
```

## running it to display command-line help
```
docker run --rm --user opencadc:opencadc -v /path/to/external/config:/config:ro \
    cadc-access-control-admin:latest /cadc-access-control-admin/bin/cadc-access-control-admin --help
```
Important: in the above usage, the args **replace** the CMD from the Dockerfile so you have to include it here.
This could be improved, but the ENTRYPOINT is provided by the base image and does some setup before executing
the CMD.... TBD.
