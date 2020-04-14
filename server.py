import random
import string
import cherrypy
import astroalign as aa
from PIL import Image
from math import tan
from matplotlib import image
import numpy as np
import matplotlib.pyplot as plt
import os.path
from pathlib import Path 
import time
import math
import datetime
from scipy.ndimage import rotate

debug = True


def image2Array(uri, rotAng):
    a = Image.open(uri)
    aBw = a.convert('L')
    if rotAng is not None:
        aBw = rotate(aBw, angle=rotAng, reshape=False)
    imgData = np.asarray(aBw)
    aBin = imgData  # (imgData > THRESHOLD_VALUE) * 1.0
    if debug is True:
        print(aBin.shape)
    return aBin


def timeLapsedInMins(url1, url2):
    aCtime = time.ctime(os.path.getmtime(url1))
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
        print("File:{} Rotated:{:.2f} deg".format(Path(aData).name, rotate))
    return rotate


def computeAzDrift(img1, img2):
    Caz = 58.3079
    d1 = image2Array(img1, 90)
    d2 = image2Array(img2, 90)
    p, (_, _) = aa.find_transform(d1, d2)
    rotate = p.rotation * 180.0 / np.pi
    d2 = image2Array(img2, rotate)
    p, (pos_img, pos_img_rot) = aa.find_transform(d1, d2)
    timeLapsed = timeLapsedInMins(img1, img2)
    drift = p.translation[1]
    adjust = Caz*drift/timeLapsed
    candidate = sorted(pos_img,
                       key=lambda x: x[0] if drift < 0 else x[1],
                       reverse=True if drift > 0 else False)[0]

    if debug is True:
        for (x1, y1), (x2, y2) in zip(pos_img, pos_img_rot):
            print("S({:.2f}, {:.2f}) --> D({:.2f}, {:.2f}) xDiff: {:.2f}  yDiff: {:.2f}".format(
                x1, y1, x2, y2,  (x2-x1), (y2-y1)))

    return (timeLapsed, drift, adjust, candidate[0], candidate[1])


def formateAzDrift(img1, img2):
    (timeL, drift, adjust, cx, cy) = computeAzDrift(img1, img2)
    str = "duration:{:.2f},drift:{:.2f},move:{:.2f},candidate:{:.2f} * {:.2f}".format(
        timeL, drift, adjust, cx, cy)
    return str


def computeAzError(img1, img2, img3):
    (_, _, adjust, _, _) = computeAzDrift(img1, img2)
    d1 = image2Array(img1, 90)
    d3 = image2Array(img3, 90)
    p, (_, _) = aa.find_transform(d1, d3)
    rotate = p.rotation * 180.0 / np.pi
    d3 = image2Array(img3, rotate)
    p, (_, _) = aa.find_transform(d1, d3)
    driftX = p.translation[0]
    return adjust, abs(adjust) - abs(driftX)


def formateAzError(img1, img2, img3):
    a, b = computeAzError(img1, img2, img3)
    str = "gap:{:.2f},displacement:{:.2f}".format(b, a)
    return str


def computeAlDrift(url1, url2, ra, starLocation):
    d1 = image2Array(url1, 0)
    d2 = image2Array(url2, 0)
    p, (_, _) = aa.find_transform(d1, d2)
    rotate = p.rotation * 180.0 / np.pi
    d2 = image2Array(url2, rotate)
    p, (_, _) = aa.find_transform(d1, d2)
    timeL = timeLapsedInMins(url1, url2)
    theta = ra
    Calt = 229*tan(int(theta.strip()))
    yDrift = p.translation[1]
    adjustment = (Calt/timeL + 1)*yDrift if starLocation is 'E' else (Calt/timeL - 1)*yDrift
    return (timeL, yDrift, adjustment)


def formateAlDrift(url1, url2, ra, starLocation):
    (timeL, yDrift, adjustment) = computeAlDrift(url1, url2, ra, starLocation)
    str = "duration:{:.2f},drift:{:.2f},move:{:.2f}".format(
        timeL, yDrift, adjustment)
    return str


def computeAlError(url1, url2, url3, ra, starLocation):
    (timeL, yDrift, adjustment) = computeAlDrift(url1, url2, ra, starLocation)
    d1 = image2Array(url1,0)
    d3 = image2Array(url3,0)
    p, (_, _) = aa.find_transform(d1, d3)
    rotate = p.rotation * 180.0 / np.pi
    d3 = image2Array(url3, rotate)
    p, (_, _) = aa.find_transform(d1, d3)
    error = abs(adjustment) - abs(p.translation[1])
    return adjustment, error

def formateAlError(url1, url2, url3, ra, starLocation):
    error = computeAlError(url1, url2, url3, ra, starLocation)
    str = "gap:{:.2f},displacement:{:.2f}".format(error,0)
    return str


cherrypy.config.update({
    'server.socket_port': 8082,
})
conf = {'/': {
    'tools.staticdir.on': True,
    'tools.staticdir.dir': 'C:\\Users\\arun\\astro\\dist',
    'tools.staticdir.index': "index.html"

}}

class Server(object):

    @cherrypy.expose
    def az(self, root=None, img1=None, img2=None):
        az = formateAzDrift(root+os.path.sep+img1, root+os.path.sep+img2)
        return az

    @cherrypy.expose
    def azCheck(self, root=None, img1=None, img2=None, img3=None):
        az = formateAzError(root+os.path.sep+img1, root+os.path.sep+img2, root+os.path.sep+img3)
        return az

    @cherrypy.expose
    def al(self, root, img1, img2, starLocation, ra):
        al = formateAlDrift(root+os.path.sep+img1,
                               root+os.path.sep+img2, ra, starLocation)
        return al

    @cherrypy.expose
    def alCheck(self,root,  img1, img2,img3, starLocation, ra):
        al = formateAlError(root+os.path.sep+img1,
                               root+os.path.sep+img2,
                               root+os.path.sep+img3,
                                ra, starLocation)
        return al


if __name__ == '__main__':
    cherrypy.quickstart(Server(), '/', conf)
