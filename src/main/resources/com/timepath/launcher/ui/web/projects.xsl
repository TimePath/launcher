<?xml version="1.0" encoding="UTF-8"?>

<!-- http://www.bootstrapcdn.com/ -->

<!DOCTYPE xsl:stylesheet [
        <!-- 	<!ENTITY % entities SYSTEM "http://www.w3.org/2003/entities/2007/w3centities-f.ent"> -->
        <!-- 	%entities; -->
        <!ENTITY times "&#x000D7;">
        ]>

<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:exsl="http://exslt.org/common"
                extension-element-prefixes="exsl"
                exclude-result-prefixes="exsl"
                version="1.0">

    <xsl:output method="html" version="5.0" encoding="UTF-8" indent="yes"/>

    <xsl:variable name="barClass" select="'breadcrumb '"/>

    <xsl:template match="/">
        <xsl:text disable-output-escaping='yes'>&lt;!DOCTYPE html></xsl:text>
        <html>
            <head>
                <meta charset="UTF-8"/>
                <title>Design test</title>
                <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no"/>
                <xsl:call-template name="styles"/>
                <xsl:call-template name="scripts"/>
            </head>
            <body>
                <header>
                    <xsl:call-template name="header"/>
                </header>
                <section>
                    <xsl:call-template name="body"/>
                </section>
                <footer>
                    <xsl:call-template name="footer"/>
                </footer>
            </body>
        </html>
    </xsl:template>

    <xsl:template name="styles">
        <link rel="stylesheet" type="text/css" href="css/bootstrap.min.css"/>

        <style id="themeLight"><![CDATA[
			@import 'css/bootstrap-theme.min.css';

			.breadcrumb.downloading { border-left: 4px solid #5bc0de }	/* Default .progress-bar-info	*/
			.breadcrumb.done { border-left: 4px solid #5cb85c }			/* Default .progress-bar-success	*/
			.breadcrumb.old { border-left: 4px solid #f0ad4e }			/* Default .progress-bar-warning	*/
			.breadcrumb.new { border-left: 4px solid #999 }				/* Default .badge	*/
		]]></style>

        <style id="themeDark"><![CDATA[
			@import 'css/bootstrap-slate.min.css';

			.breadcrumb.downloading { border-left: 4px solid #428bca }	/* Default .progress-bar	*/
			.breadcrumb.done { border-left: 4px solid #5cb85c }			/* Default .progress-bar-success	*/
			.breadcrumb.old { border-left: 4px solid #f89406 }			/* Slate .progress-bar-warning	*/
			.breadcrumb.new { border-left: 4px solid #7a8288 }				/* Slate .badge	*/


			.progress-bar-info { background-color: #428bca }			/* Default	*/

			.breadcrumb { color: #fff }									/* Custom	*/

			.label { color: #444 }										/* Custom	*/
			.label-info { background-color: #428bca }					/* Default	*/
			.label-primary { background-color: #aaa }					/* Custom	*/

			.alert-info {												/* Darkly	*/
				background-color: #3498db;
				border-color: #3498db;
				color: #ffffff;
			}

			#shutdown { border: none }

			input[type=text]::-webkit-input-placeholder	{ color: #c8c8c8 }
			input[type=text]::-moz-placeholder			{ color: #c8c8c8 }
			input[type=text]:-moz-placeholder			{ color: #c8c8c8 }
			input[type=text]:-ms-input-placeholder		{ color: #c8c8c8 }

			input[type=text] {
				color: white;
				border-color: rgba(0,0,0,0.6);
				text-shadow: 1px 1px 1px rgba(0,0,0,0.3);
				background: linear-gradient(#313539, #3a3f44 40%, #484e55);
				background-repeat: no-repeat;
				filter: progid:DXImageTransform.Microsoft.gradient(startColorstr='#ff313539', endColorstr='#ff484e55', GradientType=0);
				filter: none;
			}
		]]></style>

        <style><![CDATA[
			/* Hacks */

			/* XSL exit button hack */
			.navbar-nav.navbar-right:last-child { margin-right: 0 }

			/* XSL program button hack */
			[data-original-title]:after { content: "" }

			/* Button group spacing */
			.btn-group + .btn-group { margin-left: 5px }

			/* Webkit vertical-align */
			@media (min-width: 768px) {
				/*
				.row {
					display: table-row;
				}

				.row > * {
					float: none;
					display: table-cell;
					vertical-align: middle;
				}

				.progress { margin-top: 0 }

				*/
				/*
				.file > .row > div:nth-child(2) { padding-left: 0 }
				*/
			}

			#shutdown:after { content: " "attr(data-original-title) }

			@media (min-width: 768px) {
				#shutdown:after { content: "" }
			}

			/* Layout */

			@media (max-width: 480px) {
				.col-xxs-12 {
					display: block;
					float: none;
					width: 100%;
				}

				.col-xxs-6 {
					display: block;
					width: 50%;
				}
			}

			/* Static header body push */
			body { padding-top: 70px; }

			/* Menu */
			.program, .dependency { margin-bottom: 0 }
			.dependencies { padding-left: 9px }
			.files { padding: 9px 15px }
			.files > .list-group { margin-bottom: 0 }

			.progress {
				height: 10px;
				margin-top: 8px;
				margin-bottom: 0px;
			}

			* {
				-webkit-touch-callout: none;
				-webkit-user-select: none;
				-khtml-user-select: none;
				-moz-user-select: none;
				-ms-user-select: none;
				user-select: none;
			}

			#content .alert,
			#content strong,
			#content span,
			#content h1,
			#content h2,
			#content h3,
			#content h4,
			#content h5,
			#content h6,
			#content p,
			#content a
			{
				-webkit-touch-callout: default;
				-webkit-user-select: text;
				-khtml-user-select: text;
				-moz-user-select: text;
				-o-user-select: text;
				user-select: text;
			}
		]]></style>
    </xsl:template>

    <xsl:template name="scripts">
        <script src="js/jquery-1.11.0.min.js" type="text/javascript"/>

        <script src="js/bootstrap.min.js" type="text/javascript"/>

        <script src="js/jquery.searchable-1.1.0.min.js" type="text/javascript"/>

        <script type="text/javascript"><![CDATA[
			(function loop() {
				var now = new Date();
				var hour = now.getHours();
				var day = [8, 12+6];
				var daytime = day[0] > hour || hour >= day[1];
				// daytime = !daytime;
				$('#themeDark').prop('disabled', !daytime);
				$('#themeLight').prop('disabled', daytime);

				var changeover = ((60 - now.getMinutes()) * 60 - now.getSeconds()) * 1000;
				setTimeout(loop, changeover);
			}());

			var slideTime = 500;
			function addAlert(message, level, parent) {
				if(!message) return;
				level = level || 'info';
				parent = parent || '#alerts';
				var a = $(document.createElement('div'))
					.addClass('alert alert-' + level)
					.append(
						$(document.createElement('button'))
						.attr({
							type: 'button',
							'data-dismiss': 'alert',
							'aria-hidden': true
						}).addClass('close').text('×') // &times;
					)
					.append(
						$(document.createElement('div'))
						.html(message)
					)
					.hide().alert();

				var removeElement = function(e) {
					if (e) e.preventDefault();
					a.slideUp(slideTime, function() {
						a.trigger('closed.bs.alert').remove();
					});
				};

				a.on('close.bs.alert', removeElement).appendTo(parent).slideDown(slideTime);

				a.setTimeout = function(time) {
					setTimeout(removeElement, time);
				};

				return a;
			}

			window.alert = addAlert;

			$(function() {
				$('[data-original-title]').tooltip({
					container: 'body'
				});
				// Hack
				$('[data-original-title].disabled').css({
					'pointer-events': 'visible'
				});

				var hidden = [];
				$( '#programs' ).searchable({
					searchField		: '#search-apps',
					selector		: 'article',
					childSelector	: '.program',
					hide			: function( elem ) {
						elem.slideUp(slideTime);

						hidden.push(elem);
					},
					show			: function( elem ) {
						elem.slideDown(slideTime);
					},
					onSearchActive	: function( elem, term ) {
						hidden = [];
					},
					onSearchEmpty	: function( elem ) {
						$(hidden).each(function() {
							$(this).hide().slideDown(slideTime);
						});
						hidden = [];
					}
				});

				var welcome = $(document.createElement('div'))
				.append($(document.createElement('h4'))
					.text('Welcome!')
				)
				.append($(document.createElement('p'))
					.append('To my new ')
					.append($(document.createElement('a'))
						.attr('href', '#').addClass('alert-link')
						.text('launcher')
					)
				);

				setTimeout(function() {
					addAlert(welcome, 'info').setTimeout(300000);
					var source = new EventSource("events");
					source.onmessage = function(event) {
						alert(event.data).setTimeout(5000);
					};
				}, 500);

				//<!-- TODO: proxy ajax callback -->
				/*$.ajax({
					url: "http://dl.dropboxusercontent.com/u/42745598/doc/java/launcher.html",
					type: "GET",
					success: function(data) {
						alert( data );
					},
					error: function(xhr, status) {
						alert('AJAX error');
					}
				});*/
			});
		]]></script>
    </xsl:template>

    <xsl:template name="header">
        <div id="header">
            <div class="navbar navbar-default navbar-fixed-top">
                <div class="container">
                    <div class="navbar-header">
                        <button type="button" class="navbar-toggle" data-toggle="collapse"
                                data-target=".navbar-responsive-collapse">
                            <span class="icon-bar"/>
                            <span class="icon-bar"/>
                            <span class="icon-bar"/>
                        </button>
                        <a class="navbar-brand" href="#">TimePath</a>
                    </div>
                    <div class="navbar-collapse collapse navbar-responsive-collapse">
                        <ul class="nav navbar-nav navbar-left">
                            <li class="active">
                                <a href="#">
                                    <span class="glyphicon glyphicon-th-list"/>
                                    Programs
                                    <span class="badge">2</span>
                                </a>
                            </li>
                            <li>
                                <a href="#">About</a>
                            </li>
                            <li class="dropdown">
                                <a href="#" class="dropdown-toggle" data-toggle="dropdown">Extras
                                    <b class="caret"/>
                                </a>
                                <ul class="dropdown-menu">
                                    <li>
                                        <a href="#">Action</a>
                                    </li>
                                    <li class="divider"/>
                                    <li class="dropdown-header">Dropdown header</li>
                                    <li>
                                        <a href="#">Separated link</a>
                                    </li>
                                </ul>
                            </li>
                        </ul>

                        <form class="navbar-form navbar-left">
                            <div class="form-group" role="search">
                                <input type="text" class="form-control" placeholder="Search" id="search-apps" value=""
                                       autocomplete="off"/>
                            </div>
                        </form>

                        <ul class="nav navbar-nav navbar-right">
                            <li>
                                <a id="shutdown" href="/shutdown" data-placement="bottom" data-original-title="Shutdown">
                                    <span class="glyphicon glyphicon-off"/>
                                </a>
                            </li>
                        </ul>
                    </div>
                </div>
            </div>
        </div>
    </xsl:template>

    <xsl:template name="body">
        <!-- container-fluid -->
        <div class="container" id="content">
            <section id="alerts"/>
            <section id="programs">
                <xsl:for-each select="/root/programs/entry">
                    <xsl:sort select="translate(@name, 'abcdefghijklmnopqrstuvwxyz','ABCDEFGHIJKLMNOPQRSTUVWXYZ')"
                              order="ascending"/>
                    <article>
                        <xsl:call-template name="output-program"/>
                    </article>
                </xsl:for-each>
            </section>
        </div>
    </xsl:template>

    <xsl:template name="footer">
    </xsl:template>

    <xsl:variable name="root" select="/root"/>

    <xsl:template name="output-program">
        <xsl:variable name="req">
            <xsl:call-template name="join"/>
        </xsl:variable>

        <xsl:variable name="i" select="position()"/>
        <xsl:variable name="istr">prog<xsl:value-of select="$i"/>
        </xsl:variable>

        <!-- TODO: base on size, not file count -->
        <xsl:variable name="downloadables" select="exsl:node-set($req)/depends/entry/download"/>
        <xsl:variable name="progressProgram" select="sum($downloadables/@progress) div count($downloadables)"/>

        <xsl:variable name="progressProgramBarClass">
            <xsl:choose>
                <xsl:when test="$progressProgram >= 100">progress-bar-success</xsl:when>
                <xsl:otherwise>progress-bar-info</xsl:otherwise>
            </xsl:choose>
        </xsl:variable>

        <xsl:variable name="progressProgramDOM">
            <xsl:if test="$progressProgram > 0">
                <div class="progress progress-striped active">
                    <div class="progress-bar progress-bar-info">
                        <xsl:attribute name="style">width: <xsl:value-of select="$progressProgram"/>%
                        </xsl:attribute>
                        <xsl:attribute name="class">progress-bar
                            <xsl:value-of select="$progressProgramBarClass"/>
                        </xsl:attribute>
                    </div>
                </div>
            </xsl:if>
        </xsl:variable>

        <xsl:variable name="toolbarDOM">
            <!-- TODO: disabled class: cog, trash. TODO: star-open -->
            <div class="btn-toolbar">
                <div class="pull-right">
                    <div class="btn-group">
                        <a class="btn btn-default btn-sm" role="button" data-placement="bottom" data-original-title="Start">
                            <span class="glyphicon glyphicon-play"/>
                        </a>
                        <a class="btn btn-default btn-sm" role="button" data-placement="bottom" data-original-title="Favourite">
                            <span class="glyphicon glyphicon-star"/>
                        </a>
                        <a class="btn btn-default btn-sm" role="button" data-placement="bottom" data-original-title="Options">
                            <span class="glyphicon glyphicon-cog"/>
                        </a>
                        <a class="btn btn-default btn-sm disabled" role="button" data-placement="bottom"
                           data-original-title="Remove">
                            <span class="glyphicon glyphicon-trash"/>
                        </a>
                        <a class="btn btn-default btn-sm" role="button" data-placement="bottom" data-original-title="Info">
                            <span class="glyphicon glyphicon-info-sign"/>
                        </a>
                    </div>
                    <div class="btn-group">
                        <a class="btn btn-default btn-sm" role="button" data-toggle="collapse" data-placement="left"
                           data-original-title="Expand">
                            <xsl:attribute name="href">#<xsl:value-of select="$istr"/>
                            </xsl:attribute>
                            <span class="glyphicon glyphicon-chevron-down"/>
                        </a>
                    </div>
                </div>
            </div>
        </xsl:variable>

        <!-- TODO: other status -->
        <!-- priority: new, old, downloading, done-->
        <xsl:variable name="status">
            <xsl:choose>
                <xsl:when test="$progressProgram >= 100">done</xsl:when>
                <xsl:when test="$progressProgram > 0">downloading</xsl:when>
                <xsl:otherwise>new</xsl:otherwise>
            </xsl:choose>
        </xsl:variable>

        <section>
            <xsl:attribute name="class">
                program
                <xsl:value-of select="$barClass"/>
                <xsl:value-of select="$status"/>
            </xsl:attribute>
            <div class="row">
                <div class="col-xxs-12 col-xs-5 col-sm-4 col-md-4 col-lg-3">
                    <span>
                        <xsl:value-of select="@name"/>
                    </span>
                </div>
                <div class="col-xxs-12 col-xs-7 col-sm-5 col-sm-push-3 col-md-4 col-md-push-4 col-lg-3 col-lg-push-6">
                    <xsl:copy-of select="$toolbarDOM"/>
                </div>
                <div class="col-xxs-12 col-xs-12 col-sm-3 col-sm-pull-5 col-md-4 col-md-pull-4 col-lg-6 col-lg-pull-3">
                    <xsl:copy-of select="$progressProgramDOM"/>
                </div>
            </div>
        </section>

        <section class="dependencies">
            <div class="accordion-body collapse">
                <xsl:attribute name="id">
                    <xsl:value-of select="$istr"/>
                </xsl:attribute>

                <xsl:for-each select="exsl:node-set($req)/depends/entry">
                    <xsl:variable name="jstr">dep<xsl:value-of select="$i"/>-<xsl:value-of select="position()"/>
                    </xsl:variable>
                    <xsl:call-template name="output-dep">
                        <xsl:with-param name="jstr" select="$jstr"/>
                    </xsl:call-template>
                </xsl:for-each>
            </div>
        </section>
    </xsl:template>

    <xsl:template name="output-dep">
        <xsl:param name="jstr"/>

        <xsl:variable name="depName">
            <xsl:value-of select="@name"/>
        </xsl:variable>

        <xsl:variable name="progressLibFiles" select="download"/>
        <xsl:variable name="progressLib" select="sum($progressLibFiles/@progress) div count($progressLibFiles)"/>
        <xsl:variable name="downloading" select="count($progressLibFiles/@progress)"/>

        <xsl:variable name="progressLibState">
            <xsl:choose>
                <xsl:when test="$progressLib >= 100">done</xsl:when>
                <xsl:when test="$downloading > 0">downloading</xsl:when>
                <xsl:otherwise>new</xsl:otherwise>
            </xsl:choose>
        </xsl:variable>

        <xsl:variable name="progressLibBarClass">
            <xsl:choose>
                <xsl:when test="$progressLib >= 100">progress-bar progress-bar-success</xsl:when>
                <xsl:otherwise>progress-bar progress-bar-info</xsl:otherwise>
            </xsl:choose>
        </xsl:variable>

        <section>
            <xsl:attribute name="class">
                dependency
                <xsl:value-of select="$barClass"/>
                <xsl:value-of select="$progressLibState"/>
            </xsl:attribute>

            <div class="row">
                <div class="col-xs-10 col-sm-2">
                    <span>
                        <xsl:value-of select="$depName"/>
                    </span>
                </div>

                <div class="col-xs-2 col-sm-2 col-sm-push-8">
                    <div class="pull-right">
                        <a class="btn btn-default btn-sm" role="button" data-toggle="collapse" data-placement="left"
                           data-original-title="Expand">
                            <xsl:attribute name="href">#<xsl:value-of select="$jstr"/>
                            </xsl:attribute>

                            <span class="glyphicon glyphicon-chevron-down"/>
                        </a>
                    </div>
                </div>

                <div class="col-xs-12 col-sm-8 col-sm-pull-2">
                    <xsl:if test="$downloading > 0">
                        <div class="progress progress-striped active">
                            <div>
                                <xsl:attribute name="style">width: <xsl:value-of select="$progressLib"/>%
                                </xsl:attribute>
                                <xsl:attribute name="class">
                                    <xsl:value-of select="$progressLibBarClass"/>
                                </xsl:attribute>
                            </div>
                        </div>
                    </xsl:if>
                </div>
            </div>
        </section>

        <div class="accordion-body collapse">
            <xsl:attribute name="id">
                <xsl:value-of select="$jstr"/>
            </xsl:attribute>

            <section class="files">
                <ul class="list-group">
                    <xsl:for-each select="$progressLibFiles">
                        <xsl:call-template name="output-file">
                            <xsl:with-param name="downloading" select="$downloading"/>
                        </xsl:call-template>
                    </xsl:for-each>
                </ul>
            </section>
        </div>
    </xsl:template>

    <xsl:template name="output-file">
        <xsl:param name="downloading"/>

        <xsl:variable name="filenameSplit">
            <xsl:call-template name="split">
                <xsl:with-param name="list" select="@url"/>
                <xsl:with-param name="delimiter" select="'/'"/>
            </xsl:call-template>
        </xsl:variable>

        <xsl:variable name="filename">
            <xsl:value-of select="exsl:node-set($filenameSplit)/token[last()]"/>
        </xsl:variable>
        <xsl:variable name="progressFile">
            <xsl:choose>
                <xsl:when test="@progress">
                    <xsl:value-of select="@progress"/>
                </xsl:when>
                <xsl:otherwise>0</xsl:otherwise>
            </xsl:choose>
        </xsl:variable>

        <xsl:variable name="progressFileBarClass">
            <xsl:choose>
                <xsl:when test="$progressFile >= 100">progress-bar progress-bar-success</xsl:when>
                <xsl:otherwise>progress-bar progress-bar-info</xsl:otherwise>
            </xsl:choose>
        </xsl:variable>


        <li class="list-group-item">
            <section class="file">
                <div class="row">
                    <div class="col-xxs-6 col-xs-6 col-sm-2">
                        <a> <!-- style="overflow: hidden; text-overflow: ellipsis; white-space: nowrap;" -->
                            <xsl:attribute name="href">
                                <xsl:value-of select="@url"/>
                            </xsl:attribute>

                            <xsl:value-of select="$filename"/>
                        </a>
                    </div>
                    <xsl:if test="$downloading > 0">
                        <div class="col-xxs-6 col-xs-6 col-sm-2 col-sm-push-8">
                            <xsl:choose>
                                <!-- TODO -->
                                <xsl:when test="$progressFile >= 100">
                                    <span class="pull-right label label-success">
                                        <span class="glyphicon glyphicon-ok"/>
                                        Updated
                                    </span>
                                </xsl:when>
                                <xsl:when test="$progressFile >= 0">
                                    <span class="pull-right label label-info">
                                        <span class="glyphicon glyphicon-download-alt"/>
                                        Downloading
                                    </span>
                                </xsl:when>
                                <xsl:otherwise>
                                    <span class="pull-right label label-warning">
                                        <span class="glyphicon glyphicon-exclamation-sign"/>
                                        Update
                                    </span>
                                </xsl:otherwise>
                            </xsl:choose>
                        </div>
                        <div class="col-xs-12 col-sm-8 col-sm-pull-2">
                            <div class="progress progress-striped active">
                                <div>
                                    <xsl:attribute name="style">width: <xsl:value-of select="$progressFile"/>%
                                    </xsl:attribute>
                                    <xsl:attribute name="class">
                                        <xsl:value-of select="$progressFileBarClass"/>
                                    </xsl:attribute>
                                </div>
                            </div>
                        </div>
                    </xsl:if>
                </div>
            </section>
        </li>
    </xsl:template>

    <!-- Functions -->

    <xsl:template name="join">
        <!-- Do recursion (with dupes) -->
        <xsl:variable name="reqDupes">
            <xsl:call-template name="joinRec"/>
        </xsl:variable>

        <!-- Get names. TODO: merge with above -->
        <xsl:variable name="reqNames">
            <tokens>
                <xsl:for-each select="exsl:node-set($reqDupes)/depends/entry/@name">
                    <token>
                        <xsl:value-of select="."/>
                    </token>
                </xsl:for-each>
            </tokens>
        </xsl:variable>

        <!-- Deduplicate -->
        <xsl:variable name="reqNamesUniq">
            <tokens>
                <!-- Create context -->
                <xsl:for-each select="exsl:node-set($reqNames)/tokens">
                    <xsl:for-each select="token[not(.=preceding::*)]">
                        <token>
                            <xsl:value-of select="."/>
                        </token>
                    </xsl:for-each>
                </xsl:for-each>
            </tokens>
        </xsl:variable>

        <!-- Return -->
        <depends>
            <xsl:for-each select="exsl:node-set($reqNamesUniq)/tokens/token">
                <xsl:variable name="depName">
                    <xsl:value-of select="."/>
                </xsl:variable>
                <xsl:copy-of select="$root/libs/entry[@name=$depName]"/>
            </xsl:for-each>
        </depends>
    </xsl:template>

    <xsl:template name="joinRec">
        <!-- dependencies this program has -->
        <xsl:variable name="depends">
            <xsl:call-template name="split">
                <xsl:with-param name="list" select="@depends"/>
                <xsl:with-param name="delimiter" select="','"/>
            </xsl:call-template>
        </xsl:variable>

        <depends>
            <xsl:for-each select="exsl:node-set($depends)/token">
                <xsl:variable name="depName">
                    <xsl:value-of select="."/>
                </xsl:variable>
                <xsl:for-each select="$root/libs/entry[@name=$depName]"> <!-- Context switch -->
                    <!-- Append self to parent -->
                    <xsl:copy-of select="."/>

                    <!-- Recurse -->
                    <xsl:variable name="dependRecursive">
                        <xsl:call-template name="joinRec"/>
                    </xsl:variable>

                    <!-- Add self deps to parent -->
                    <xsl:for-each select="exsl:node-set($dependRecursive)/depends/entry">
                        <xsl:copy-of select="."/>
                    </xsl:for-each>
                </xsl:for-each>
            </xsl:for-each>
        </depends>
    </xsl:template>

    <xsl:template name="split">
        <xsl:param name="list"/>
        <xsl:param name="delimiter"/>

        <xsl:variable name="newlist">
            <xsl:choose>
                <xsl:when test="contains($list, $delimiter)">
                    <xsl:value-of select="normalize-space($list)"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:value-of select="concat(normalize-space($list), $delimiter)"/>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        <xsl:variable name="first" select="substring-before($newlist, $delimiter)"/>
        <xsl:variable name="remaining" select="substring-after($newlist, $delimiter)"/>
        <xsl:variable name="count" select="position()"/>

        <token>
            <xsl:value-of select="$first"/>
        </token>

        <xsl:if test="string-length($remaining) > 0">
            <xsl:call-template name="split">
                <xsl:with-param name="list" select="$remaining"/>
                <xsl:with-param name="delimiter" select="$delimiter"/>
            </xsl:call-template>
        </xsl:if>
    </xsl:template>

</xsl:stylesheet>
