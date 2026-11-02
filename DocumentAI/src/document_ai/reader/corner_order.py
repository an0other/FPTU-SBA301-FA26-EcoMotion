"""Geometric ordering independent of printed content: topmost then clockwise."""
import cv2
import numpy as np


def order_corners(points):
    pts=np.asarray(points,dtype=float)
    if pts.shape!=(4,2) or not np.isfinite(pts).all():raise ValueError('Four finite points required')
    center=pts.mean(axis=0)
    pts=pts[np.argsort(np.arctan2(pts[:,1]-center[1],pts[:,0]-center[0]))]
    if not cv2.isContourConvex(pts.astype(np.float32)) or cv2.contourArea(pts.astype(np.float32))<1:raise ValueError('Invalid quadrilateral')
    # image coordinates: clockwise is positive area. Anchor minimum y; ties minimum x.
    if cv2.contourArea(pts.astype(np.float32),oriented=True)<0:pts=pts[::-1]
    start=min(range(4),key=lambda i:(pts[i,1],pts[i,0]))
    return np.roll(pts,-start,axis=0)


def quarter_turn_points(points,width,height,angle):
    pts=np.asarray(points,dtype=float)
    if angle==0:return pts.copy()
    if angle==90:return np.column_stack((pts[:,1],width-1-pts[:,0]))
    if angle==180:return np.column_stack((width-1-pts[:,0],height-1-pts[:,1]))
    if angle==270:return np.column_stack((height-1-pts[:,1],pts[:,0]))
    raise ValueError('Quarter-turn angle required')
