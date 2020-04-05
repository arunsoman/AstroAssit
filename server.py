import random
import string

import cherrypy

import astroalign as aa
from PIL import Image
from math import tan
from matplotlib import image
import numpy as np
import matplotlib.pyplot as plt
import os.path, time, math, datetime
from scipy.ndimage import rotate

debug=True

def image2Array(uri, rotAng) :

    THRESHOLD_VALUE = 100

    a = Image.open(uri)
    aBw = a.convert('L')
    if rotAng is not None:
        aBw = rotate(aBw, angle=rotAng, reshape=False)
    imgData = np.asarray(aBw)
    aBin = imgData #(imgData > THRESHOLD_VALUE) * 1.0
    if debug is True:
        print(aBin.shape)
    return aBin

def timeLapsedInMins(url1, url2):
    aCtime =   time.ctime(os.path.getmtime(url1))
    bCtime = time.ctime(os.path.getmtime(url2))

    acTime = datetime.datetime.strptime(aCtime, "%a %b %d %H:%M:%S %Y")
    bcTime = datetime.datetime.strptime(bCtime, "%a %b %d %H:%M:%S %Y")
    timeDiff = (bcTime - acTime).total_seconds() / 60.0

    if debug is True:
        print("Name {} created time {}".format(os.path.basename(url1), aCtime))
        print("Name {} created time {}".format(os.path.basename(url2), bCtime))
        print("Time lapsed in min:{:.2f}".format(timeDiff))
    return timeDiff

def computeRotAng(aData, url2):
    bData = image2Array(url2, 0)
    p, (_, _) = aa.find_transform(aData, bData)
    rotate = p.rotation * 180.0 / np.pi
    if debug is True:
        print("Rotation: {:.2f} deg".format(rotate))
    return rotate

def process(d1, url2,  rotAng=None):
    aData = d1
    if rotAng is None:
        rotAng = computeRotAng(aData,url2)
    bData = image2Array(url2,rotAng)
    return aa.find_transform(aData, bData)


class azimuthAdjustment:
    adjust = None
    deltaX = None
    Caz = 58.3079
    candidate = None
    source = None
    str = None

    def __init__(self, url1, url2):
        aData = image2Array(url1, 0)
        p, (pos_img, pos_img_rot) = process(aData, url2)
        timeL = timeLapsedInMins(url1, url2)
        drift = p.translation[1]
        self.adjust = self.Caz*drift/timeL
        self.candidate = sorted(pos_img,  
            key=lambda x: x[0] if drift < 0 else x[1], 
            reverse=True if drift > 0 else False)[0]
        
        if debug is True:
            for (x1, y1), (x2, y2) in zip(pos_img, pos_img_rot):
                print("S({:.2f}, {:.2f}) --> D({:.2f}, {:.2f}) xDiff: {:.2f}  yDiff: {:.2f}".format(x1, y1, x2, y2,  (x2-x1), (y2-y1)))

        self.str =  "duration:{:.2f},drift:{:.2f},move:{:.2f},candidate:{:.2f} * {:.2f}".format(
            timeL, drift,
            
            self.adjust,
            self.candidate[0], self.candidate[1]) 
        
        self.source = (aData, url1)
        self.deltaX = drift

    def check(self, url):
        p, (pos_img, pos_img_rot) = process(self.source[0], url, None)
        deltaX = p.translation[0]
        str = "gap:{:.2f},displacement:{:.2f}".format(abs(self.adjust - abs(deltaX)), deltaX)
        return str




class AltitudeAlignment(object):
    adjustment = None
    pDrift = None
    source = None
    starLocation = None

    def __init__(self, url1, url2, ra, starLocation):
        aData = image2Array(url1, 0)
        p, (pos_img, pos_img_rot) = process(aData, url2)
        timeL = timeLapsedInMins(url1, url2)
        self.starLocation = starLocation
        self.source = (aData, url1)
        theta = ra
        Calt = 229*tan(int(theta.strip()))
        yDrift = p.translation[1]

        if starLocation is 'E':
            self.adjustment = (Calt/timeL + 1)*yDrift
        else:
            self.adjustment = (Calt/timeL - 1)*yDrift
        self.str= "duration:{:.2f},drift:{:.2f},move:{:.2f}".format(timeL, yDrift, self.adjustment)


    def convert2Degrees(ra):
        #todo
        return 30

    def check(self, url, ra):
        p, (pos_img, pos_img_rot) = self.process(self.source[1], url)
        deltaY = p.translation[1]
        str = "gap:{:.2f},displacement:{:.2f}".format(abs(self.adjust - abs(deltaY)), deltaY)
        return str


cherrypy.config.update({
                        'server.socket_port': 8082,
})
conf ={'/':{
                        'tools.staticdir.on' :True,
                        'tools.staticdir.dir' : 'C:\\Users\\arun\\astro\\dist',
                        'tools.staticdir.index' : "index.html"

               }               }

az = None
base = None
al = None
alBase = None
class Server(object):
    

    @cherrypy.expose
    def az(self, root=None, img1 = None, img2=None):
        print(root)
        print(img1)
        print(img2)
        base = root
        az = azimuthAdjustment(root+os.path.sep+img1, root+os.path.sep+img2)
        return az.str

    @cherrypy.expose
    def azCheck(self, img1 = None):
        return az.check(base+os.path.sep+img1)
    
    @cherrypy.expose
    def al(self, root, img1, img2, starLocation, ra):
        print(root)
        print(img1)
        print(img2)
        alBase = root
        al = AltitudeAlignment(root+os.path.sep+img1, root+os.path.sep+img2, ra, starLocation)
        return al.str

    @cherrypy.expose
    def alCheck(self, img1 = None):
        return al.check(base+os.path.sep+img1)


if __name__ == '__main__':
    cherrypy.quickstart(Server(),'/', conf)