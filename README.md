facelets-lite
=============

Facelets Lite is an open source Facelets implementation written for the cloud. 
It is fast, easy to configure, and has no external dependencies.

The Facelets template language is a powerful template language, that was developed in 2005 by Jakob Hookom to replace Java Server Pages (JSP). It is perfect for wrapping HTML fragments as custom tags and reuse them later. Today, this beautiful gem is hidden somewhere deep in the J2EE stack.

The Facelets lite implementation strips Facelets of all J2EE/JSF dependencies. It provides a simple, clean and straight-forward library, while preserving and improving the powerful Facelets templating features. And dropping everything else.

Ready for cloud environments
----------------------------

Facelets lite is a self-contained jar with zero start-up time. It is configured by code only, and requires no external config files. There is no classpath magic. It does not use threads, file I/O or other restricted APIs.

Facelets lite has been designed for heavy parallel usage and high throughput.

In production
-------------

Facelets lite is used by:
* [distance24](http://www.distance24.org)
* [Biz-q](http://www.biz-q.com)
