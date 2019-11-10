#!/usr/bin/env python
from __future__ import print_function
import os
import sys
import time
import base64

try:
    # py3
    from urllib.parse import urlparse, urlencode, quote
    from urllib.request import urlopen, Request
    from urllib.error import HTTPError
except ImportError:
    # py2
    from urlparse import urlparse
    from urllib import urlencode, quote
    from urllib2 import urlopen, Request, HTTPError

#from exceptions import Exception
from email.mime.base import MIMEBase
from email.mime.multipart import MIMEMultipart
from email.mime.application  import MIMEApplication

from email.encoders import encode_noop

import json
def json2python(data):
    try:
        return json.loads(data)
    except:
        pass
    return None
python2json = json.dumps

class MalformedResponse(Exception):
    pass
class RequestError(Exception):
    pass

class Client(object):
    default_url = 'http://nova.astrometry.net/api/'

    def __init__(self,
                 apiurl = default_url):
        self.session = None
        self.apiurl = apiurl

    def get_url(self, service):
        return self.apiurl + service

    def send_request(self, service, args={}, file_args=None):
        '''
        service: string
        args: dict
        '''
        if self.session is not None:
            args.update({ 'session' : self.session })
        #print('Python:', args)
        json = python2json(args)
        #print('Sending json:', json)
        url = self.get_url(service)
        print('Sending to URL:', url)

        # If we're sending a file, format a multipart/form-data
        if file_args is not None:
            # Make a custom generator to format it the way we need.
            from io import BytesIO
            try:
                # py3
                from email.generator import BytesGenerator as TheGenerator
            except ImportError:
                # py2
                from email.generator import Generator as TheGenerator

            m1 = MIMEBase('text', 'plain')
            m1.add_header('Content-disposition',
                          'form-data; name="request-json"')
            m1.set_payload(json)
            m2 = MIMEApplication(file_args[1],'octet-stream',encode_noop)
            m2.add_header('Content-disposition',
                          'form-data; name="file"; filename="%s"'%file_args[0])
            mp = MIMEMultipart('form-data', None, [m1, m2])

            class MyGenerator(TheGenerator):
                def __init__(self, fp, root=True):
                    # don't try to use super() here; in py2 Generator is not a
                    # new-style class.  Yuck.
                    TheGenerator.__init__(self, fp, mangle_from_=False,
                                          maxheaderlen=0)
                    self.root = root
                def _write_headers(self, msg):
                    # We don't want to write the top-level headers;
                    # they go into Request(headers) instead.
                    if self.root:
                        return
                    # We need to use \r\n line-terminator, but Generator
                    # doesn't provide the flexibility to override, so we
                    # have to copy-n-paste-n-modify.
                    for h, v in msg.items():
                        self._fp.write(('%s: %s\r\n' % (h,v)).encode())
                    # A blank line always separates headers from body
                    self._fp.write('\r\n'.encode())

                # The _write_multipart method calls "clone" for the
                # subparts.  We hijack that, setting root=False
                def clone(self, fp):
                    return MyGenerator(fp, root=False)

            fp = BytesIO()
            g = MyGenerator(fp)
            g.flatten(mp)
            data = fp.getvalue()
            headers = {'Content-type': mp.get('Content-type')}

        else:
            # Else send x-www-form-encoded
            data = {'request-json': json}
            #print('Sending form data:', data)
            data = urlencode(data)
            data = data.encode('utf-8')
            #print('Sending data:', data)
            headers = {}

        request = Request(url=url, headers=headers, data=data)

        try:
            f = urlopen(request)
            txt = f.read()
            print('Got json:', txt)
            result = json2python(txt)
            #print('Got result:', result)
            stat = result.get('status')
            print('Got status:', stat)
            if stat == 'error':
                errstr = result.get('errormessage', '(none)')
                raise RequestError('server error message: ' + errstr)
            return result
        except HTTPError as e:
            print('HTTPError', e)
            txt = e.read()
            open('err.html', 'wb').write(txt)
            print('Wrote error text to err.html')

    def login(self, apikey):
        args = { 'apikey' : apikey }
        result = self.send_request('login', args)
        sess = result.get('session')
        print('Got session:', sess)
        if not sess:
            raise RequestError('no session in result')
        self.session = sess

    def _get_upload_args(self, **kwargs):
        args = {}
        for key,default,typ in [('allow_commercial_use', 'd', str),
                                ('allow_modifications', 'd', str),
                                ('publicly_visible', 'y', str),
                                ('scale_units', None, str),
                                ('scale_type', None, str),
                                ('scale_lower', None, float),
                                ('scale_upper', None, float),
                                ('scale_est', None, float),
                                ('scale_err', None, float),
                                ('center_ra', None, float),
                                ('center_dec', None, float),
                                ('parity',None,int),
                                ('radius', None, float),
                                ('downsample_factor', None, int),
                                ('positional_error', None, float),
                                ('tweak_order', None, int),
                                ('crpix_center', None, bool),
                                ('invert', None, bool),
                                ('image_width', None, int),
                                ('image_height', None, int),
                                ('x', None, list),
                                ('y', None, list),
                                ]:
            if key in kwargs:
                val = kwargs.pop(key)
                val = typ(val)
                args.update({key: val})
            elif default is not None:
                args.update({key: default})
        print('Upload args:', args)
        return args

    
    def upload(self, fn=None, **kwargs):
        args = self._get_upload_args(**kwargs)
        file_args = None
        if fn is not None:
            try:
                f = open(fn, 'rb')
                file_args = (fn, f.read())
            except IOError:
                print('File %s does not exist' % fn)
                raise
        return self.send_request('upload', args, file_args)

    def job_status(self, job_id, justdict=False):
        result = self.send_request('jobs/%s' % job_id)
        if justdict:
            return result
        stat = result.get('status')
        if stat == 'success':
            result = self.send_request('jobs/%s/calibration' % job_id)
            print('Calibration:', result)
        return stat,  result

    def sub_status(self, sub_id, justdict=False):
        result = self.send_request('submissions/%s' % sub_id)
        if justdict:
            return result
        return result.get('status')


import threading

class ClientPoxy:
    def __init__(self, key, serUri=None):
        if serUri is not None:
            self.c = Client(serUri)
        else:
            self.c = Client()
        self.c.login(key)
        self.results=dict()

    def uploadNtarck(self, upload, **kwargs):
        c = self.c
        fileName = os.path.basename(upload).split('.')[0]
        res = dict()
        res["fName"] = fileName
        print('processing ------------------- {}\n'.format(fileName))
        kwargs = dict(
            allow_commercial_use='d',
            allow_modifications='d',
            publicly_visible='y')

        upres = c.upload(upload, **kwargs)
        
        stat = upres['status']
        if stat != 'success':
            print('Upload failed: status', stat)
            print(upres)
            sys.exit(-1)

        sub_id = upres['subid']

        if sub_id is None:
            print("Can't --wait without a submission id or job id!")
            sys.exit(-1)

        job_id = None
        while True:
            stat = c.sub_status(sub_id, justdict=True)
            print('Got status:', stat)
            jobs = stat.get('jobs', [])
            if len(jobs):
                for j in jobs:
                    if j is not None:
                        break
                if j is not None:
                    print('Selecting job id', j)
                    job_id = j
                    break
            time.sleep(10)

        while True:
            stat = c.job_status(job_id, justdict=True)
            print('Got job status:', stat)
            if stat.get('status','') in ['success']:
                success = (stat['status'] == 'success')
                stat, result = c.job_status(job_id)
                res["cal"] = result
                print("Done calibrating {}".format(fileName))
                break
            time.sleep(10)
        self.results[fileName] = res

import glob
import os
from astropy import units as u
from astropy.coordinates import SkyCoord

if __name__ == '__main__':
    print("Running with args %s"%sys.argv)
    import optparse
    parser = optparse.OptionParser()
    parser.add_option('--server', dest='server', default=Client.default_url,
                      help='Set server base URL (eg, %default)')
    parser.add_option('--apikey', '-k', dest='apikey',
                      help='API key for Astrometry.net web service; if not given will check AN_API_KEY environment variable')
    parser.add_option('--upload', '-u', dest='upload', help='Upload a file')
    
    opt,args = parser.parse_args()

    if opt.apikey is None:
        # try the environment
        opt.apikey = os.environ.get('AN_API_KEY', "XXXXXXXXXXXXX")
    if opt.apikey is None:
        parser.print_help()
        print()
        print('You must either specify --apikey or set AN_API_KEY')
        sys.exit(-1)

    args = {}
    args['apiurl'] = opt.server
    # c = Client(**args)
    # c.login(opt.apikey)
    
    files_path = os.path.join("C:\\Users\\arun-lp\\Pictures\\BackyardEOS", '*jpg')
    files = sorted(
        glob.iglob(files_path), key=os.path.getctime, reverse=True) 
    print ("file 1 ", files[0])
    print ("file 2 ", files[1])
    opt.upload = files[0] #"C:\\Users\\arun-lp\\Pictures\\BackyardEOS\\LIGHT_5s_16000iso_f5-6_+29c_20191001-01h00m57s864ms.jpg"
    opt.upload1 = files[1] #"C:\\Users\\arun-lp\\Pictures\\BackyardEOS\\LIGHT_5s_16000iso_f5-6_+29c_20191001-00h56m20s802ms.jpg"
    # opt.wait = True

    cp = ClientPoxy('mdwfxouvoednelzl')#, 'http://localhost:8080/api/')
    # cp.uploadNtarck(opt)

    t1 = threading.Thread(target=cp.uploadNtarck, args=(opt.upload,)) 
    t1.start() 

    t2 = threading.Thread(target=cp.uploadNtarck, args=(opt.upload1,)) 
    t2.start() 
  
    # wait until thread 1 is completely executed 
    t1.join() 
    # wait until thread 2 is completely executed 
    t2.join()

    # print(cp.results)

    keys = cp.results.keys()

    d1 = cp.results[keys[0]]['cal']
    d2 = cp.results[keys[1]]['cal']

    raDiff = d1['ra'] - d2['ra']
    decDiff = d1['dec'] - d2['dec']

    c1 = SkyCoord(ra=d1['ra']*u.degree, dec=d1['dec']*u.degree, frame='icrs')
    c2 = SkyCoord(ra=d2['ra']*u.degree, dec=d2['dec']*u.degree, frame='icrs')
    diff = SkyCoord(ra=abs(raDiff)*u.degree, dec=abs(decDiff)*u.degree, frame='icrs').to_string('hmsdms').split()
    raDiffFormat =  '-{}'.format(diff[0]) if ( raDiff < 0)  else '{}'.format(diff[0])
    decDiffFormat =  '-{}'.format(diff[1]) if ( raDiff < 0)  else '{}'.format(diff[1])
    print("Summary")
    print('p1 coordinates {}'.format(c1.to_string('hmsdms')))
    print('p2 coordinates {}'.format(c2.to_string('hmsdms')))
    print('Alignment Error: ra:{} dec:{}'.format(raDiffFormat, decDiffFormat ) )
